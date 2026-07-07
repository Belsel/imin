import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import PublicGroupsSection from '../components/PublicGroupsSection'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../lib/apiClient'

/**
 * Public, unauthenticated landing page rendered at "/" for logged-out
 * visitors (see the auth-state branch in App.tsx). Mostly static marketing
 * content: the only data fetching this page performs is one unauthenticated
 * `GET /api/groups/public-recommendations` call, made by the child
 * `<PublicGroupsSection />` component, to show a few real groups as a trust
 * signal — no other backend calls happen anywhere on this page.
 *
 * Single job: convince a cold visitor (arriving from a shared link, a
 * search result, or just the bare domain, on mobile or desktop) in a few
 * seconds that ImIn is a real-world group/activity finder worth an account,
 * then send them to /register or /login.
 */
export default function LandingPage() {
  const { loginAsDemo } = useAuth()
  const navigate = useNavigate()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleDemoLogin() {
    setError(null)
    setIsSubmitting(true)
    try {
      await loginAsDemo()
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start the demo session. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <header className="bg-primary px-6 py-16 sm:py-24">
        <div className="mx-auto flex max-w-3xl flex-col items-center gap-6 text-center">
          <div>
            <h1 className="text-5xl font-extrabold font-display text-on-primary tracking-tight">ImIn</h1>
            <p className="mt-2 text-sm text-on-primary/70 tracking-wide uppercase">
              Find your people. Get ImIn.
            </p>
          </div>
          <p className="max-w-xl text-lg font-body text-on-primary/90">
            ImIn helps you find groups built around things you actually want to do — then gets you
            there, turn by turn.
          </p>
          <div className="flex flex-wrap justify-center gap-3">
            <Link
              to="/register"
              className="rounded-full bg-surface px-6 py-2.5 font-medium font-body text-primary transition-colors motion-safe:hover:bg-surface/90 focus:outline-none focus:ring-2 focus:ring-on-primary focus:ring-offset-2 focus:ring-offset-primary"
            >
              Get ImIn
            </Link>
            <Link
              to="/login"
              className="rounded-full border border-on-primary/60 px-6 py-2.5 font-medium font-body text-on-primary transition-colors motion-safe:hover:bg-on-primary/10 focus:outline-none focus:ring-2 focus:ring-on-primary focus:ring-offset-2 focus:ring-offset-primary"
            >
              Log in
            </Link>
            <button
              type="button"
              onClick={handleDemoLogin}
              disabled={isSubmitting}
              className="rounded-full border border-on-primary/60 px-6 py-2.5 font-medium font-body text-on-primary transition-colors motion-safe:hover:bg-on-primary/10 focus:outline-none focus:ring-2 focus:ring-on-primary focus:ring-offset-2 focus:ring-offset-primary disabled:opacity-50"
            >
              {isSubmitting ? 'Starting demo…' : 'Try demo account'}
            </button>
          </div>
          {error && <p className="text-sm text-error">{error}</p>}
        </div>
      </header>

      <PublicGroupsSection />

      <main className="mx-auto max-w-2xl px-6 py-16">
        <h2 className="mb-10 text-center text-2xl font-bold font-display text-text">How ImIn works</h2>
        <ol className="flex flex-col">
          {steps.map((step, index) => {
            const isLast = index === steps.length - 1
            return (
              <li key={step.title} className="flex gap-4">
                <div className="flex flex-col items-center">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold font-display text-on-primary">
                    {index + 1}
                  </span>
                  {!isLast && <span className="w-px flex-1 bg-primary/30" aria-hidden="true" />}
                </div>
                <div className={isLast ? undefined : 'pb-10'}>
                  <h3 className="text-lg font-bold font-display text-text">{step.title}</h3>
                  <p className="mt-1 text-sm font-body text-text-muted">{step.body}</p>
                </div>
              </li>
            )
          })}
        </ol>
      </main>
    </div>
  )
}

const steps = [
  {
    title: 'Find your groups',
    body: 'Browse groups built around real activities — running, bouldering, sketching, whatever — recommended by your location and interests, or start one yourself.',
  },
  {
    title: 'Coordinate in the chat',
    body: "Every group has its own chat, visible only to its members, so you can work out what's actually happening next without noise from anyone outside the group.",
  },
  {
    title: 'Show up with real directions',
    body: 'Activities are pinned to a real map location with turn-by-turn routing to get you there — not just an address.',
  },
]
