import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getPublicRecommendations } from '../lib/apiClient'
import type { PublicGroupRecommendationResponse } from '../lib/apiClient'

/**
 * Public landing page's "real groups" trust-signal section (see
 * specs/public-group-recommendations/spec.md). Fetches the unauthenticated
 * top-6-by-member-count recommendation feed on mount and renders it as a
 * card grid; every card links to /register (not a group detail page, which
 * is behind ProtectedRoute and would just redirect an anonymous visitor to
 * /login). Renders null until the fetch resolves with at least one group --
 * a failed fetch or an empty result both collapse into the same "don't
 * render" state, so this section can never appear broken on the landing
 * page's initial view.
 */
export default function PublicGroupsSection() {
  const [groups, setGroups] = useState<PublicGroupRecommendationResponse[] | null>(null)

  useEffect(() => {
    let cancelled = false
    getPublicRecommendations()
      .then((results) => {
        if (!cancelled) setGroups(results)
      })
      .catch(() => {
        // Network error / non-2xx / timeout: degrade to "nothing to show",
        // same as an empty array -- never surface an error here (spec
        // Requirement 4: this section must never look broken).
        if (!cancelled) setGroups([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (!groups || groups.length === 0) {
    return null
  }

  return (
    <section className="mx-auto max-w-4xl px-6 py-16">
      <h2 className="mb-8 text-center text-2xl font-bold font-display text-text">
        Real groups, right now
      </h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {groups.map((group) => (
          <Link
            key={group.id}
            to="/register"
            className="block rounded-2xl border border-border bg-surface p-4 shadow-sm transition-colors motion-safe:hover:border-primary/60 motion-safe:hover:shadow"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium text-text font-body">{group.name}</span>
              <span className="shrink-0 text-sm text-text-muted font-body">
                {group.memberCount} member{group.memberCount === 1 ? '' : 's'}
              </span>
            </div>
            {group.description && (
              <p className="mt-1 line-clamp-2 text-sm text-text-muted font-body">{group.description}</p>
            )}
            {group.categories.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-2">
                {group.categories.map((category) => (
                  <span
                    key={category.id}
                    className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary"
                  >
                    {category.name}
                  </span>
                ))}
              </div>
            )}
          </Link>
        ))}
      </div>
    </section>
  )
}
