import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useAuth } from '../context/AuthContext'

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const { loginWithToken } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      setError('No token was provided by the Google sign-in redirect.')
      return
    }

    let cancelled = false
    loginWithToken(token)
      .then(() => {
        if (!cancelled) navigate('/', { replace: true })
      })
      .catch(() => {
        if (!cancelled) setError('Failed to complete Google sign-in. Please try again.')
      })

    return () => {
      cancelled = true
    }
  }, [token, loginWithToken, navigate])

  if (error) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6">
        <div className="w-full max-w-sm rounded-2xl bg-surface p-6 shadow-sm border border-border text-center">
          <h1 className="mb-2 text-2xl font-bold font-display text-text">Sign-in failed</h1>
          <p className="mb-4 text-sm text-error">{error}</p>
          <Link to="/login" className="text-primary hover:underline font-body">
            Back to login
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6">
      <p className="text-text-muted font-body">Completing sign-in…</p>
    </div>
  )
}
