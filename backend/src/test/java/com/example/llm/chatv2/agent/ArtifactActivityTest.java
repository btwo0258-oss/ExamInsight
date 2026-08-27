package com.example.llm.chatv2.agent;

import com.example.llm.chatv2.artifact.ArtifactDraftService;
import com.example.llm.chatv2.artifact.ArtifactModels.DocumentDraftInput;
import com.example.llm.chatv2.artifact.ArtifactModels.ToolResult;
import com.example.llm.chatv2.artifact.ArtifactModels.Type;
import com.example.llm.chatv2.repository.ChatV2Repository.RunExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ArtifactActivityTest {
    @Test
    void announcesGenerationBeforeRunningTheToolAndDoesNotRepeatForADuplicateCall() throws Exception {
        ArtifactDraftService service = mock(ArtifactDraftService.class);
        RunExecutionContext context = mock(RunExecutionContext.class);
        when(context.runExternalId()).thenReturn("run-test");
        ControlledChatAgent agent = new ControlledChatAgent(null, service, null, null);
        var activities = new ArrayList<ControlledChatAgent.AgentActivity>();
        Consumer<ControlledChatAgent.AgentActivity> listener = activities::add;
        DocumentDraftInput input = new DocumentDraftInput("测试文档", "# 标题");
        when(service.createDocument(context, input)).thenAnswer(invocation -> {
            assertThat(activities).hasSize(1);
            assertThat(activities.get(0).stage()).isEqualTo("artifact-started");
            assertThat(activities.get(0).details()).containsEntry("type", "DOCUMENT");
            return new ToolResult("DRAFT", "artifact-test", Type.DOCUMENT, "测试文档", null, "Ready");
        });
        Class<?> sessionClass = Class.forName(ControlledChatAgent.class.getName() + "$ArtifactSession");
        var constructor = sessionClass.getDeclaredConstructor(ControlledChatAgent.class, RunExecutionContext.class, Consumer.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(agent, context, listener);
        var document = sessionClass.getDeclaredMethod("document", DocumentDraftInput.class);
        document.setAccessible(true);
        document.invoke(session, input);
        document.invoke(session, input);

        assertThat(activities).extracting(ControlledChatAgent.AgentActivity::stage)
                .containsExactly("artifact-started", "artifact-created");
        assertThat(activities.get(1).details().get("generationId"))
                .isEqualTo(activities.get(0).details().get("generationId"));
        verify(service, times(1)).createDocument(context, input);
    }
}
