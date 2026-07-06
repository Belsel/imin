import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import NavBar from '../components/NavBar'
import { ApiError, listDirectThreads } from '../lib/apiClient'
import type { DirectThreadResponse } from '../lib/apiClient'

/**
 * DM inbox: lists the caller's existing direct-chat threads (other
 * participant's display name + a last-message preview, per
 * `DirectThreadResponse`). Threads are created implicitly on first message
 * (see `DirectChatService`), so a user with no conversations yet simply sees
 * an empty list — there's no separate "start a new thread" flow here; that
 * happens via the "Message" link on a group's member list
 * (`GroupDetailPage`), which navigates straight to `/messages/:userId`.
 */
export default function DirectMessagesListPage() {
  const [threads, setThreads] = useState<DirectThreadResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setIsLoading(true)
      setLoadError(null)
      try {
        const result = await listDirectThreads()
        if (!cancelled) setThreads(result)
      } catch (err) {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : 'Could not load your messages.')
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-2xl p-6">
        <h1 className="mb-6 text-3xl font-bold font-display text-text">Messages</h1>

        {isLoading && <p className="text-sm text-text-muted">Loading…</p>}
        {loadError && <p className="text-sm text-error">{loadError}</p>}

        {!isLoading && !loadError && (
          <div className="rounded-2xl bg-surface p-6 shadow-sm border border-border">
            {threads.length === 0 && (
              <p className="text-sm text-text-muted">
                You have no conversations yet. Message someone from a group's member list to start one.
              </p>
            )}
            <ul className="flex flex-col gap-2">
              {threads.map((thread) => (
                <li key={thread.threadId}>
                  <Link
                    to={`/messages/${thread.otherUserId}`}
                    className="flex items-center justify-between rounded-xl border border-border px-3 py-3 transition-colors motion-safe:hover:bg-background"
                  >
                    <div>
                      <p className="text-text font-body">{thread.otherUserDisplayName ?? 'Unknown user'}</p>
                      {thread.lastMessageBody && (
                        <p className="mt-0.5 truncate text-sm text-text-muted font-body">{thread.lastMessageBody}</p>
                      )}
                    </div>
                    {thread.lastMessageAt && (
                      <span className="ml-3 shrink-0 text-xs text-text-muted font-body">
                        {new Date(thread.lastMessageAt).toLocaleString()}
                      </span>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  )
}
