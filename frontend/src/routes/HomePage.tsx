import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../context/AuthContext'
import GroupListItem from '../components/GroupListItem'
import NavBar from '../components/NavBar'
import { ApiError, getMyGroups } from '../lib/apiClient'
import type { GroupResponse } from '../lib/apiClient'

/**
 * Authenticated home/dashboard route. Confirms auth works end to end,
 * links into the groups/friends/profile pages, and surfaces the groups the
 * user has already joined.
 */
export default function HomePage() {
  const { user } = useAuth()

  const [myGroups, setMyGroups] = useState<GroupResponse[] | null>(null)
  const [myGroupsError, setMyGroupsError] = useState<string | null>(null)
  const [isLoadingMyGroups, setIsLoadingMyGroups] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function loadMyGroups() {
      setIsLoadingMyGroups(true)
      setMyGroupsError(null)
      try {
        const results = await getMyGroups()
        if (!cancelled) setMyGroups(results)
      } catch (err) {
        if (!cancelled) {
          setMyGroupsError(
            err instanceof ApiError ? err.message : 'Could not load your groups.',
          )
        }
      } finally {
        if (!cancelled) setIsLoadingMyGroups(false)
      }
    }

    loadMyGroups()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="flex flex-col items-center gap-6 p-6 mt-16">
        <div className="text-center">
          <p className="text-xs font-semibold font-body tracking-[0.2em] uppercase text-primary mb-2">WELCOME BACK</p>
          <h1 className="text-4xl font-extrabold font-display text-text">{user?.displayName}</h1>
          <p className="mt-2 text-sm text-text-muted font-body">{user?.email}</p>
        </div>
        <div className="flex flex-wrap gap-3 justify-center">
          <Link
            to="/groups"
            className="rounded-full bg-primary px-5 py-2.5 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Browse groups
          </Link>
          <Link
            to="/friends"
            className="rounded-full border border-border px-5 py-2.5 font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Friends &amp; blocks
          </Link>
          <Link
            to="/profile"
            className="rounded-full border border-border px-5 py-2.5 font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Profile
          </Link>
        </div>

        <section className="w-full max-w-2xl mt-6">
          <h2 className="mb-3 text-2xl font-bold font-display text-text">Your Groups</h2>
          {isLoadingMyGroups && <p className="text-sm text-text-muted font-body">Loading your groups…</p>}
          {!isLoadingMyGroups && myGroupsError && (
            <p className="rounded-lg bg-warning px-3 py-2 text-sm text-warning-text font-body">{myGroupsError}</p>
          )}
          {!isLoadingMyGroups && !myGroupsError && myGroups && myGroups.length === 0 && (
            <p className="text-sm text-text-muted font-body">You haven't joined any groups yet.</p>
          )}
          {!isLoadingMyGroups && !myGroupsError && myGroups && myGroups.length > 0 && (
            <ul className="flex flex-col gap-2">
              {myGroups.map((group) => (
                <GroupListItem key={group.id} group={group} />
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
