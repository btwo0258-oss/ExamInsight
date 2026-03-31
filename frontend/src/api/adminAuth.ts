import { adminRequest as request } from './adminRequest'

export const login = (data: any) => request.post('/user/login', data)
export const getAdminInfo = () => request.get('/user/info')
