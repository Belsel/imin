import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../lib/apiClient'

export default function RegisterPage() {
  const { register } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [pendingVerificationMessage, setPendingVerificationMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      const response = await register({ email, password, displayName })
      setPendingVerificationMessage(response.message)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (pendingVerificationMessage) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-primary p-6">
        <div className="text-center">
          <h1 className="text-5xl font-extrabold font-display text-on-primary tracking-tight">ImIn</h1>
          <p className="mt-2 text-sm text-on-primary/70 tracking-wide uppercase">Find your people. Get ImIn.</p>
        </div>
        <div className="w-full max-w-sm rounded-2xl bg-surface p-8 shadow-xl">
          <h1 className="text-2xl font-bold font-display text-text mb-6">Check your email</h1>
          <p className="text-text font-body">{pendingVerificationMessage}</p>
          <p className="mt-4 text-sm text-text-muted font-body">
            Already verified?{' '}
            <Link to="/login" className="text-primary hover:underline">
              Log in
            </Link>
          </p>
        </div>
      </div>
    )
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
        <h1 className="text-2xl font-bold font-display text-text mb-6">Create an account</h1>

        <label className="mb-3 block">
          <span className="mb-1 block text-sm font-medium font-body text-text-muted">Display name</span>
          <input
            type="text"
            required
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>

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
            minLength={8}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>

        {error && <p className="mb-4 text-sm text-error">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
        >
          {isSubmitting ? 'Registering…' : 'Register'}
        </button>

        <p className="mt-4 text-sm text-text-muted font-body">
          Already have an account?{' '}
          <Link to="/login" className="text-primary hover:underline">
            Log in
          </Link>
        </p>
      </form>
    </div>
  )
}
