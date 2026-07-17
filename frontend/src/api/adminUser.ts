import { adminRequest as request } from './adminRequest'

export type AdminUser = {
  id: number
  username: string
  nickname: string
  status: string
  registerTime?: string
  lastLogin?: string
  hasForgotRequest?: boolean
  stats?: {
    convCount?: number
    kbCount?: number
    fileCount?: number
    mindMapCount?: number
    hasForgotRequest?: number
  }
  settings?: {
    theme?: string
    defaultModel?: string
  }
}

export const getUserList = (params: Record<string, unknown>) => request.get('/api/admin/users', { params }) as unknown as Promise<AdminUser[]>
export const getUserDetail = (id: number) => request.get(`/api/admin/users/${id}`) as unknown as Promise<AdminUser>
export const updateStatus = (id: number, status: string) => request.put(`/api/admin/users/${id}/status`, { status })
export const resetPassword = (id: number) => request.post(`/api/admin/users/${id}/reset-password`)
export const handleResetRequest = (id: number, action: number) => request.post(`/api/admin/users/${id}/handle-reset-request`, { action })
export const updateUserSettings = (userId: number, settings: any) => request.put(`/api/admin/user-settings/${userId}`, settings)
