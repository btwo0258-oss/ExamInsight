import axios from 'axios'

export const adminRequest = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

adminRequest.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

adminRequest.interceptors.response.use(
  (response) => {
    // 兼容可能存在的后端Result封装
    const data = response.data;
    if (data && data.code !== undefined && data.data !== undefined) {
      if (data.code === 200 || data.code === 0) {
        return data.data;
      }
      return Promise.reject(new Error(data.message || 'Error'));
    }
    return data;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('admin_token')
      window.location.href = '/admin/login'
    }
    return Promise.reject(error)
  }
)
