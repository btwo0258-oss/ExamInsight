import { isMockDataSource } from '@/config/dataSource'
import type { MindMapRenderConfig, MindMapTreeNode } from '@/types/contracts/artifact'

export const MIND_MAP_ACCENT = '#4f9b8d'

function neutralMindMapThemeConfig() {
  const dark = typeof document !== 'undefined' && document.documentElement.dataset.theme === 'dark'
  const background = dark ? '#151518' : '#ffffff'
  const surface = dark ? '#27272a' : '#f5f5f4'
  const text = dark ? '#f0f0ee' : '#333333'
  const line = dark ? '#8b8b8b' : '#777777'
  const border = dark ? '#b8b8b8' : '#555555'
  return {
    backgroundColor: background,
    lineColor: line,
    lineWidth: 2,
    associativeLineColor: line,
    associativeLineWidth: 2,
    borderColor: border,
    borderWidth: 1,
    root: {
      fillColor: dark ? '#e6e6e3' : '#333333',
      color: dark ? '#151518' : '#ffffff',
      borderColor: dark ? '#e6e6e3' : '#333333',
      borderWidth: 0,
      borderRadius: 5,
      fontSize: 16,
      fontWeight: 'bold',
      paddingX: 14,
      paddingY: 7,
    },
    second: {
      fillColor: surface,
      color: text,
      borderColor: border,
      borderWidth: 1,
      borderRadius: 6,
      fontSize: 14,
      paddingX: 12,
      paddingY: 7,
      marginX: 100,
      marginY: 28,
    },
    node: {
      fillColor: surface,
      color: text,
      borderColor: border,
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

function clonePlain<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

/** Keep the complete authoritative tree, including backend-generated node styles. */
export function mindMapRenderData(tree: MindMapTreeNode): MindMapTreeNode {
  return clonePlain(tree)
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
  if (config) return clonePlain(config)
  if (isMockDataSource) return mockMindMapRenderConfig()
  return { theme: 'classic', layout: 'logicalStructure' }
}

/** Preview/editor-safe neutral palette shared by generated and uploaded maps. */
export function resolveNeutralMindMapRenderConfig(config?: MindMapRenderConfig | null): MindMapRenderConfig {
  const source = config || {}
  return {
    theme: source.theme || 'classic',
    layout: source.layout || 'logicalStructure',
    themeConfig: neutralMindMapThemeConfig(),
  }
}
