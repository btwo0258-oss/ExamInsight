export type SseEvent = {
  id?: string
  event?: string
  data: string
}

function parseEventBlock(block: string): SseEvent[] {
  const lines = block.split(/\r?\n/)
  const events: SseEvent[] = []
  let eventName: string | undefined
  let eventId: string | undefined
  let dataLines: string[] = []

  function flush() {
    if (dataLines.length === 0) return
    events.push({ id: eventId, event: eventName, data: dataLines.join('\n') })
    dataLines = []
    eventName = undefined
    eventId = undefined
  }

  for (const line of lines) {
    if (!line) continue
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('id:')) {
      eventId = line.slice('id:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
      continue
    }
  }
  flush()
  return events
}

export async function* parseSseEventStream(response: Response): AsyncGenerator<SseEvent> {
  if (!response.body) return

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    while (true) {
      const separator = /\r?\n\r?\n/.exec(buffer)
      if (!separator || separator.index === undefined) break

      const block = buffer.slice(0, separator.index)
      buffer = buffer.slice(separator.index + separator[0].length)
      const events = parseEventBlock(block)
      for (const evt of events) {
        if (!evt.data) continue
        yield evt
      }
    }
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    for (const evt of parseEventBlock(buffer)) {
      if (evt.data) yield evt
    }
  }
}

export async function* parseSseTextStream(response: Response): AsyncGenerator<string> {
  for await (const evt of parseSseEventStream(response)) {
    if (evt.data === '[DONE]') return
    if (evt.event === 'error') throw new Error(evt.data)
    if (evt.event && evt.event !== 'message') continue

    const text = normalizeChunk(evt.data)
    if (text) yield text
  }
}

function normalizeChunk(data: string): string {
  const trimmed = data.trim()
  if (!trimmed) return ''

  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const json = JSON.parse(trimmed) as {
        delta?: unknown
        content?: unknown
        text?: unknown
        choices?: unknown
      }

      const direct = json.delta ?? json.content ?? json.text
      if (typeof direct === 'string') return direct

      const choices = json.choices as Array<{ delta?: { content?: string } }> | undefined
      const v = choices?.[0]?.delta?.content
      if (typeof v === 'string') return v
    } catch {
      return trimmed
    }
  }

  return data
}
