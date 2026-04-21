type SseEvent = {
  event?: string
  data: string
}

function parseEventBlock(block: string): SseEvent[] {
  const lines = block.split(/\r?\n/)
  const events: SseEvent[] = []
  let eventName: string | undefined
  let dataLines: string[] = []

  function flush() {
    if (dataLines.length === 0) return
    events.push({ event: eventName, data: dataLines.join('\n') })
    dataLines = []
    eventName = undefined
  }

  for (const line of lines) {
    if (!line) continue
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
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

export async function* parseSseTextStream(response: Response): AsyncGenerator<string> {
  if (!response.body) return

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    while (true) {
      const sep = buffer.indexOf('\n\n')
      if (sep === -1) break

      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      const events = parseEventBlock(block)
      for (const evt of events) {
        if (!evt.data) continue
        if (evt.data === '[DONE]') return
        if (evt.event === 'error') throw new Error(evt.data)
        if (evt.event && evt.event !== 'message') continue

        const text = normalizeChunk(evt.data)
        if (text) yield text
      }
    }
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
