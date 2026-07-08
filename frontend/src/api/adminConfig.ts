import { adminRequest as request } from './adminRequest'

export const getAllConfigs = () => request.get('/api/admin/configs')
export const updateConfig = (key: string, value: string) => request.put(`/api/admin/configs/${key}`, { value })
