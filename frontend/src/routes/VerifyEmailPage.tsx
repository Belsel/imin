import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError, verifyEmail } from '../lib/apiClient'

type VerificationState = 'verifying' | 'success' | 'error'

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [state, setState] = useState<VerificationState>('verifying')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const hasAttemptedRef = useRef(false)

  useEffect(() => {
    if (!token) {
      setState('error')
      setErrorMessage('No verification token was provided. Check the link from your email.')
      return
    }

    // Guard against StrictMode's dev-only mount -> cleanup -> remount, which
    // would otherwise fire this effect twice and send the single-use
    // verification token to the backend a second time (hitting the
    // "token already used" 400 path and surfacing a false "Verification
    // failed" for a verification that actually succeeded). This guard only
    // controls whether the request is *sent* — it must not also gate
    // whether the response is allowed to update state (see below).
    if (hasAttemptedRef.current) return
    hasAttemptedRef.current = true

    // Deliberately no "cancelled" flag here. Under StrictMode's synchronous
    // mount -> cleanup -> remount, a per-invocation "cancelled" closure set
    // in cleanup gets poisoned by the very first (discarded) invocation's
    // cleanup before the real request ever resolves, permanently blocking
    // the eventual setState and leaving the page stuck on "Verifying...".
    // Since hasAttemptedRef already ensures exactly one network call is
    // ever sent, the .then/.catch below can update state unconditionally:
    // React 18+ safely no-ops a state update on a component that has
    // genuinely unmounted (no warning, no error), so there's nothing left
    // to guard against.
    verifyEmail(token)
      .then(() => {
        setState('success')
      })
      .catch((err) => {
        setState('error')
        setErrorMessage(
          err instanceof ApiError ? err.message : 'Verification failed. Please try again.',
        )
      })
  }, [token])

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6">
      <div className="w-full max-w-sm rounded-2xl bg-surface p-6 shadow-sm border border-border text-center">
        {state === 'verifying' && (
          <p className="text-text-muted font-body">Verifying your email…</p>
        )}

        {state === 'success' && (
          <>
            <h1 className="mb-2 text-2xl font-bold font-display text-text">Email verified</h1>
            <p className="mb-4 text-text font-body">
              Your email address has been verified. You can now log in.
            </p>
            <Link
              to="/login"
              className="inline-block rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            >
              Log in
            </Link>
          </>
        )}

        {state === 'error' && (
          <>
            <h1 className="mb-2 text-2xl font-bold font-display text-text">Verification failed</h1>
            <p className="mb-4 text-sm text-error">{errorMessage}</p>
            <Link to="/login" className="text-primary hover:underline font-body">
              Back to login
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
