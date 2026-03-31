import { adminRequest as request } from './adminRequest'

export const getStats = () => request.get('/admin/dashboard/stats')
export const getTrends = () => request.get('/admin/dashboard/trends')
export const getTypeDistribution = () => request.get('/admin/dashboard/types')
