import { request } from './request'

export type ModelInfo = {
  id?: string
  name: string
  label?: string
}

export async function listModels(): Promise<ModelInfo[]> {
  try {
    const res = await request.get('/api/config/model')
    return (res.data?.data ?? res.data) as ModelInfo[]
  } catch {
    return [
      { name: 'qwen-plus-2025-07-28', label: 'qwen-plus' },
      { name: 'deepseek-v3', label: 'deepseek-v3' },
    ]
  }
}
