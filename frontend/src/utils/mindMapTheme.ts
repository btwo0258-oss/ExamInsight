import { isMockDataSource } from '@/config/dataSource'
import type { MindMapRenderConfig, MindMapTreeNode } from '@/types/contracts/artifact'

export const MIND_MAP_ACCENT = '#4f9b8d'

/** Keep the complete authoritative tree, including backend-generated node styles. */
export function mindMapRenderData(tree: MindMapTreeNode): MindMapTreeNode {
  return structuredClone(tree)
}

/** Mock-only visual preset. Formal data keeps the backend-provided render configuration. */
export function mockMindMapThemeConfig() {
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

export function mockMindMapRenderConfig(): MindMapRenderConfig {
  return {
    theme: 'classic',
    layout: 'logicalStructure',
    themeConfig: mockMindMapThemeConfig(),
  }
}

export function resolveMindMapRenderConfig(config?: MindMapRenderConfig | null): MindMapRenderConfig {
  if (config) return structuredClone(config)
  if (isMockDataSource) return mockMindMapRenderConfig()
  return { theme: 'classic', layout: 'logicalStructure' }
}
