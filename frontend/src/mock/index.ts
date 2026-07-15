export * from "./student";
import { isMockDataSource } from '@/config/dataSource'

export const mockEnabled = {
  get value() {
    return isMockDataSource
  },
}

export function enableMock(enabled: boolean = true) {
  if (enabled !== isMockDataSource) {
    console.warn('Mock mode is selected at build time with VITE_DATA_SOURCE and cannot be changed at runtime.')
  }
}
