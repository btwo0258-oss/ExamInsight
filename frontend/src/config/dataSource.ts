export type DataSourceMode = 'mock' | 'api'

const configuredMode = import.meta.env.VITE_DATA_SOURCE

if (configuredMode && configuredMode !== 'mock' && configuredMode !== 'api') {
  throw new Error(`Invalid VITE_DATA_SOURCE: ${configuredMode}`)
}

export const dataSourceMode: DataSourceMode = configuredMode ?? (import.meta.env.PROD ? 'api' : 'mock')

if (import.meta.env.PROD && dataSourceMode !== 'api') {
  throw new Error('Production builds must use VITE_DATA_SOURCE=api')
}

export const isMockDataSource = dataSourceMode === 'mock'
export const isApiDataSource = dataSourceMode === 'api'
