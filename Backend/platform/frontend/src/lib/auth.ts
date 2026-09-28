import type { User } from '../types'

const TOKEN_KEY = 'platform_token'
const USER_KEY = 'platform_user'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  return JSON.parse(raw) as User
}

export function saveSession(token: string, user: User): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isTokenExpired(token: string): boolean {
  try {
    const parts = token.split('.')
    if (parts.length < 2) {
      return true
    }
    const payload = JSON.parse(atob(parts[1].replaceAll('-', '+').replaceAll('_', '/'))) as { exp?: number }
    if (typeof payload.exp !== 'number') {
      return true
    }
    return payload.exp * 1000 <= Date.now()
  } catch {
    return true
  }
}

export function isAuthenticated(): boolean {
  const token = getToken()
  if (!token) {
    return false
  }
  if (isTokenExpired(token)) {
    clearSession()
    return false
  }
  return true
}
