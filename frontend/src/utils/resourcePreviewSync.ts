import type { ArtifactInlinePreview } from '@/types/contracts/artifact'

export type ResourcePreviewUpdate = {
  resourceId: string
  artifactId?: string
  preview?: ArtifactInlinePreview
  version?: number | string
}

type Listener = (update: ResourcePreviewUpdate) => void

const listeners = new Set<Listener>()
const latestByResourceId = new Map<string, ResourcePreviewUpdate>()
const channel = typeof window === 'undefined' || typeof BroadcastChannel === 'undefined'
  ? null
  : new BroadcastChannel('examinsight:resource-preview')

function notify(update: ResourcePreviewUpdate) {
  latestByResourceId.set(update.resourceId, update)
  listeners.forEach((listener) => listener(update))
}

channel?.addEventListener('message', (event: MessageEvent<ResourcePreviewUpdate>) => {
  if (event.data?.resourceId) notify(event.data)
})

export function publishResourcePreviewUpdate(update: ResourcePreviewUpdate) {
  notify(update)
  channel?.postMessage(update)
}

export function subscribeResourcePreviewUpdates(listener: Listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function latestResourcePreviewUpdate(resourceId: string) {
  return latestByResourceId.get(resourceId)
}
