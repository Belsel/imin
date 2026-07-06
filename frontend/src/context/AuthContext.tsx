import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  ApiError,
  getMyProfile,
  getStoredToken,
  login as apiLogin,
  register as apiRegister,
  setStoredToken,
} from '../lib/apiClient'
import type { ProfileResponse, RegisterRequest, RegisterResponse } from '../lib/apiClient'

interface AuthContextValue {
  /** The current authenticated user's profile, or null if logged out. */
  user: ProfileResponse | null
  /** True while the initial session (stored-token -> profile) check is in flight. */
  isLoading: boolean
  /** Email + password login. Stores the JWT and loads the profile on success. */
  login: (email: string, password: string) => Promise<void>
  /** Registration. Does NOT log the user in — LOCAL accounts require email verification first. */
  register: (request: RegisterRequest) => Promise<RegisterResponse>
  /** Store a JWT obtained out-of-band (e.g. the Google OAuth2 redirect) and load the profile. */
  loginWithToken: (token: string) => Promise<void>
  /**
   * Replace the cached profile (e.g. after a successful `PATCH /api/users/me`)
   * so every consumer (NavBar, ProfilePage, etc.) reflects the change without
   * a full page reload or a refetch.
   */
  setUser: (profile: ProfileResponse) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<ProfileResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const loadProfile = useCallback(async () => {
    try {
      const profile = await getMyProfile()
      setUser(profile)
    } catch (error) {
      // Stored token is missing/expired/invalid — treat as logged out.
      setStoredToken(null)
      setUser(null)
      if (!(error instanceof ApiError)) {
        throw error
      }
    }
  }, [])

  useEffect(() => {
    const token = getStoredToken()
    if (!token) {
      setIsLoading(false)
      return
    }
    loadProfile().finally(() => setIsLoading(false))
  }, [loadProfile])

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await apiLogin({ email, password })
      setStoredToken(response.token)
      await loadProfile()
    },
    [loadProfile],
  )

  const loginWithToken = useCallback(
    async (token: string) => {
      setStoredToken(token)
      await loadProfile()
    },
    [loadProfile],
  )

  const register = useCallback((request: RegisterRequest) => apiRegister(request), [])

  const logout = useCallback(() => {
    setStoredToken(null)
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, isLoading, login, register, loginWithToken, setUser, logout }),
    [user, isLoading, login, register, loginWithToken, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
