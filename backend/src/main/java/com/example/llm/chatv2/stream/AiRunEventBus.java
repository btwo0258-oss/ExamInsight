package com.example.llm.chatv2.stream;

import com.example.llm.auth.security.AuthCrypto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class AiRunEventBus {
    private static final Duration RETENTION = Duration.ofMinutes(30);
    private static final long EMITTER_TIMEOUT_MS = RETENTION.toMillis();
    private static final int MAX_MEMORY_EVENTS_PER_RUN = 512;
    private static final int MAX_MEMORY_RUNS = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuthCrypto crypto;
    private final Map<String, RunState> states = new ConcurrentHashMap<>();
    private final Map<String, Object> runLocks = new ConcurrentHashMap<>();

    public AiRunEventBus(StringRedisTemplate redis, ObjectMapper objectMapper, AuthCrypto crypto) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
    }

    public String publish(String runId, String type, Object payload) {
        Object lock = runLocks.computeIfAbsent(runId, ignored -> new Object());
        synchronized (lock) {
            RunState state = states.computeIfAbsent(runId, ignored -> new RunState());
            String eventId = nextEventId(runId, state);
            StoredEvent event = new StoredEvent(eventId, type, toJson(payload), Instant.now());
            state.append(event);
            persist(runId, event);
            for (SseEmitter emitter : state.emitters) {
                send(state, emitter, event);
            }
            if (terminal(type)) {
                state.terminal = true;
                for (SseEmitter emitter : state.emitters) {
                    emitter.complete();
                }
                state.emitters.clear();
            }
            cleanupOldStates();
            return eventId;
        }
    }

    public SseEmitter subscribe(String runId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Object lock = runLocks.computeIfAbsent(runId, ignored -> new Object());
        synchronized (lock) {
            RunState state = states.computeIfAbsent(runId, ignored -> new RunState());
            long after = parseEventId(lastEventId);
            List<StoredEvent> replay = loadEvents(runId, state).stream()
                    .filter(event -> parseEventId(event.id()) > after)
                    .sorted(Comparator.comparingLong(event -> parseEventId(event.id())))
                    .toList();
            for (StoredEvent event : replay) {
                if (!send(state, emitter, event)) {
                    return emitter;
                }
            }
            boolean replayReachedTerminal = replay.stream().anyMatch(event -> terminal(event.type()));
            if (state.terminal || replayReachedTerminal) {
                emitter.complete();
                return emitter;
            }
            state.emitters.add(emitter);
            emitter.onCompletion(() -> state.emitters.remove(emitter));
            emitter.onTimeout(() -> {
                state.emitters.remove(emitter);
                emitter.complete();
            });
            emitter.onError(error -> state.emitters.remove(emitter));
        }
        return emitter;
    }

    private boolean send(RunState state, SseEmitter emitter, StoredEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.id())
                    .name(event.type())
                    .data(event.payloadJson()));
            return true;
        } catch (IOException | IllegalStateException exception) {
            state.emitters.remove(emitter);
            emitter.completeWithError(exception);
            return false;
        }
    }

    private String nextEventId(String runId, RunState state) {
        try {
            Long value = redis.opsForValue().increment(sequenceKey(runId));
            if (value != null) {
                redis.expire(sequenceKey(runId), RETENTION);
                state.sequence.accumulateAndGet(value, Math::max);
                return Long.toString(value);
            }
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable for V2 SSE sequence; using in-memory sequence: runId={}, code={}",
                    runId, exception.getClass().getSimpleName());
        }
        return Long.toString(state.sequence.incrementAndGet());
    }

    private void persist(String runId, StoredEvent event) {
        try {
            String key = eventsKey(runId);
            redis.opsForList().rightPush(key, objectMapper.writeValueAsString(event));
            redis.expire(key, RETENTION);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Redis unavailable for V2 SSE history; reconnect is process-local: runId={}, code={}",
                    runId, exception.getClass().getSimpleName());
        }
    }

    private List<StoredEvent> loadEvents(String runId, RunState state) {
        try {
            List<String> serialized = redis.opsForList().range(eventsKey(runId), 0, -1);
            if (serialized != null && !serialized.isEmpty()) {
                List<StoredEvent> result = new ArrayList<>(serialized.size());
                for (String value : serialized) {
                    result.add(objectMapper.readValue(value, StoredEvent.class));
                }
                return List.copyOf(result);
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Unable to replay V2 SSE history from Redis: runId={}, code={}",
                    runId, exception.getClass().getSimpleName());
        }
        return state.snapshot();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            return "{\"eventSerializationError\":true,\"requestId\":\"" + crypto.newExternalId() + "\"}";
        }
    }

    private long parseEventId(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean terminal(String type) {
        return List.of("run.completed", "run.failed", "run.cancelled").contains(type);
    }

    private String sequenceKey(String runId) {
        return "examinsight:v2:ai-run:" + runId + ":event-sequence";
    }

    private String eventsKey(String runId) {
        return "examinsight:v2:ai-run:" + runId + ":events";
    }

    private void cleanupOldStates() {
        if (states.size() <= MAX_MEMORY_RUNS) {
            return;
        }
        Instant cutoff = Instant.now().minus(RETENTION);
        states.entrySet().removeIf(entry -> entry.getValue().terminal
                && entry.getValue().lastUpdated.isBefore(cutoff));
        runLocks.keySet().removeIf(runId -> !states.containsKey(runId));
    }

    public record StoredEvent(String id, String type, String payloadJson, Instant createdAt) {
    }

    private static final class RunState {
        private final AtomicLong sequence = new AtomicLong();
        private final Deque<StoredEvent> events = new ArrayDeque<>();
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private volatile Instant lastUpdated = Instant.now();
        private volatile boolean terminal;

        private synchronized void append(StoredEvent event) {
            events.addLast(event);
            while (events.size() > MAX_MEMORY_EVENTS_PER_RUN) {
                events.removeFirst();
            }
            lastUpdated = event.createdAt();
        }

        private synchronized List<StoredEvent> snapshot() {
            return List.copyOf(events);
        }
    }
}
