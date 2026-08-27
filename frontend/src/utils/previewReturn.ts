import type { LocationQuery, LocationQueryRaw, RouteLocationRaw, Router } from 'vue-router'

const anchorKeys = ['returnMessageId', 'returnArtifactId', 'returnOffset'] as const
let pendingChatReturn: LocationQueryRaw | null = null

/** Only app-local destinations and explicit anchor fields travel through the editor. */
export function previewReturnQuery(query: LocationQuery | LocationQueryRaw): LocationQueryRaw {
  const path = query.returnTo
  const result: LocationQueryRaw = {
    returnTo: typeof path === 'string' && /^\/(?!\/)/.test(path) && !/[\\\u0000-\u001f]/.test(path)
      ? path : '/library',
  }
  for (const key of anchorKeys) {
    if (typeof query[key] === 'string' && query[key]) result[key] = query[key]
  }
  return result
}

export function resolvePreviewReturn(router: Router, query: LocationQuery | LocationQueryRaw): RouteLocationRaw {
  const origin = previewReturnQuery(query)
  const target = router.resolve(origin.returnTo as string)
  const destinationQuery: LocationQueryRaw = { ...target.query }
  if (target.name === 'chat-detail') {
    for (const key of anchorKeys) {
      if (origin[key]) destinationQuery[key] = origin[key]
    }
  }
  return { path: target.path, query: destinationQuery, hash: target.hash }
}

/** One small, transient fallback for browser Back; explicit close also carries query anchors. */
export function rememberChatPreviewReturn(query: LocationQueryRaw) {
  pendingChatReturn = previewReturnQuery(query)
}

export function pendingPreviewReturnFor(path: string): LocationQueryRaw | null {
  const target = pendingChatReturn?.returnTo
  return typeof target === 'string' && target.split(/[?#]/, 1)[0] === path ? pendingChatReturn : null
}

export function clearPendingPreviewReturn(path: string) {
  if (pendingPreviewReturnFor(path)) pendingChatReturn = null
}
