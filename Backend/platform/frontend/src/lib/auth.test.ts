import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, isAuthenticated, isTokenExpired, saveSession } from './auth'
import type { User } from '../types'

function jwtWithExp(expSeconds: number): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }))
  const payload = btoa(JSON.stringify({ exp: expSeconds }))
  return `${header}.${payload}.sig`
}

const user: User = {
  id: 'user-1',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  role: 'DIRECTOR',
  active: true,
}

describe('auth session', () => {
  const store: Record<string, string> = {}

  afterEach(() => {
    for (const key of Object.keys(store)) {
      delete store[key]
    }
    vi.unstubAllGlobals()
  })

  it('treats missing and expired tokens as logged out', () => {
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store[key] ?? null,
      setItem: (key: string, value: string) => {
        store[key] = value
      },
      removeItem: (key: string) => {
        delete store[key]
      },
    })

    expect(isAuthenticated()).toBe(false)
    expect(isTokenExpired('not-a-jwt')).toBe(true)

    const expired = jwtWithExp(Math.floor(Date.now() / 1000) - 60)
    saveSession(expired, user)
    expect(isAuthenticated()).toBe(false)

    const valid = jwtWithExp(Math.floor(Date.now() / 1000) + 3600)
    saveSession(valid, user)
    expect(isAuthenticated()).toBe(true)
    clearSession()
    expect(isAuthenticated()).toBe(false)
  })
})
