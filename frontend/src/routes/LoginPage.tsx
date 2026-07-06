import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext'
import { ApiError, googleOAuthUrl } from '../lib/apiClient'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [unverified, setUnverified] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setUnverified(false)
    setIsSubmitting(true)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        // The backend returns 403 specifically for "email not verified" on
        // LOCAL accounts (see AuthService.login) — surface that distinctly
        // from a generic login failure.
        setUnverified(true)
      } else {
        setError(err instanceof ApiError ? err.message : 'Login failed. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-primary p-6">
      <div className="text-center">
        <h1 className="text-5xl font-extrabold font-display text-on-primary tracking-tight">ImIn</h1>
        <p className="mt-2 text-sm text-on-primary/70 tracking-wide uppercase">Find your people. Get ImIn.</p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-2xl bg-surface p-8 shadow-xl"
      >
        <h1 className="text-2xl font-bold font-display text-text mb-6">Log in</h1>

        <label className="mb-3 block">
          <span className="mb-1 block text-sm font-medium font-body text-text-muted">Email</span>
          <input
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>

        <label className="mb-4 block">
          <span className="mb-1 block text-sm font-medium font-body text-text-muted">Password</span>
          <input
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>

        {unverified && (
          <p className="mb-4 rounded-lg bg-warning px-3 py-2 text-sm text-warning-text">
            Your email address hasn't been verified yet. Check your inbox for the verification
            link before logging in.
          </p>
        )}
        {error && <p className="mb-4 text-sm text-error">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
        >
          {isSubmitting ? 'Logging in…' : 'Log in'}
        </button>

        <a
          href={googleOAuthUrl()}
          className="mt-3 block w-full rounded-full border border-border px-4 py-2 text-center font-medium font-body text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
        >
          Sign in with Google
        </a>

        <p className="mt-4 text-sm text-text-muted font-body">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary hover:underline">
            Register
          </Link>
        </p>
      </form>
    </div>
  )
}
