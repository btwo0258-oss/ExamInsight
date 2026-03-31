import { adminRequest as request } from './adminRequest'

export const getAllConfigs = () => request.get('/admin/configs')
export const updateConfig = (key: string, value: string) => request.put(`/admin/configs/${key}`, { value })
