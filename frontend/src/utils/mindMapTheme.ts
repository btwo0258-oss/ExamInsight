import type { MindMapTreeNode } from '@/types/contracts/artifact'

export const MIND_MAP_ACCENT = '#4f9b8d'

const PRESENTATION_STYLE_KEYS = [
  'fillColor',
  'color',
  'borderColor',
  'borderWidth',
  'borderRadius',
  'fontSize',
  'fontWeight',
  'shape',
] as const

/** Content is shared; presentation-only node overrides are removed so every surface stays visually identical. */
export function mindMapRenderData(tree: MindMapTreeNode): MindMapTreeNode {
  const cloned = structuredClone(tree)
  const visit = (node: MindMapTreeNode) => {
    PRESENTATION_STYLE_KEYS.forEach((key) => delete node.data[key])
    node.children?.forEach(visit)
  }
  visit(cloned)
  return cloned
}

/** Shared by chat preview, full resource preview, and the editable canvas. */
export function mindMapThemeConfig() {
  return {
    backgroundColor: '#ffffff',
    lineColor: '#646965',
    lineWidth: 2,
    associativeLineColor: '#646965',
    associativeLineWidth: 2,
    borderColor: MIND_MAP_ACCENT,
    borderWidth: 1,
    root: {
      fillColor: MIND_MAP_ACCENT,
      color: '#ffffff',
      borderColor: MIND_MAP_ACCENT,
      borderWidth: 0,
      borderRadius: 5,
      fontSize: 16,
      fontWeight: 'bold',
      paddingX: 14,
      paddingY: 7,
    },
    second: {
      fillColor: '#ffffff',
      color: '#43504c',
      borderColor: MIND_MAP_ACCENT,
      borderWidth: 1,
      borderRadius: 6,
      fontSize: 14,
      paddingX: 12,
      paddingY: 7,
      marginX: 100,
      marginY: 28,
    },
    node: {
      fillColor: '#ffffff',
      color: '#43504c',
      borderColor: MIND_MAP_ACCENT,
      borderWidth: 1,
      borderRadius: 6,
      fontSize: 13,
      paddingX: 10,
      paddingY: 6,
      marginX: 60,
      marginY: 14,
    },
  }
}
