import { adminRequest as request } from './adminRequest'

export const login = (data: any) => request.post('/api/user/login', data)
export const getAdminInfo = () => request.get('/api/user/info')
