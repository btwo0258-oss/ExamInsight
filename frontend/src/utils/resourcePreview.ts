import type { RouteLocationRaw } from 'vue-router'

export type ResourcePreviewSource = 'library' | 'knowledge' | 'learning' | 'chat'

export function resourcePreviewRoute(
  resourceId: string,
  returnTo: string,
  source: ResourcePreviewSource,
): RouteLocationRaw {
  return {
    name: 'resource-preview',
    params: { resourceId },
    query: { returnTo, source },
  }
}
