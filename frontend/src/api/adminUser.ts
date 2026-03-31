import { adminRequest as request } from './adminRequest'

export const getUserList = (params: any) => request.get('/admin/users', { params })
export const getUserDetail = (id: number) => request.get(`/admin/users/${id}`)
export const updateStatus = (id: number, status: string) => request.put(`/admin/users/${id}/status`, { status })
export const resetPassword = (id: number) => request.post(`/admin/users/${id}/reset-password`)
export const handleResetRequest = (id: number, action: number) => request.post(`/admin/users/${id}/handle-reset-request`, { action })
export const updateUserSettings = (userId: number, settings: any) => request.put(`/admin/user-settings/${userId}`, settings)
