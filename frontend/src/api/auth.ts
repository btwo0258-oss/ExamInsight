import { authRepository } from '@/repositories/auth'
import type { ApiUser, AuthResult } from '@/repositories/auth'

export type { ApiUser, AuthResult }

export const login = authRepository.login.bind(authRepository)
export const register = authRepository.register.bind(authRepository)
export const updateProfile = authRepository.updateProfile.bind(authRepository)
export const forgotPassword = authRepository.forgotPassword.bind(authRepository)
export const getSettings = authRepository.getSettings.bind(authRepository)
export const updateSettings = authRepository.updateSettings.bind(authRepository)
