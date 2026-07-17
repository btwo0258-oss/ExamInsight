import { adminRequest as request } from './adminRequest'

export type SystemConfig = {
  configKey: string
  configValue: string
  description?: string
}

export const getAllConfigs = () => request.get('/api/admin/configs') as unknown as Promise<SystemConfig[]>
export const updateConfig = (key: string, value: string) => request.put(`/api/admin/configs/${key}`, { value })
