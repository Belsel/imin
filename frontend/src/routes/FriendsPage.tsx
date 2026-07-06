import { useEffect, useState } from 'react'
import NavBar from '../components/NavBar'
import {
  ApiError,
  listBlocks,
  listFriends,
  removeFriend,
  unblockUser,
} from '../lib/apiClient'
import type { BlockResponse, FriendResponse } from '../lib/apiClient'

/**
 * Lists the current user's friends and blocks (one-directional relationships
 * — see spec.md Resolved Questions items 1-2) with remove/unblock actions.
 * Adding a friend or blocking someone is done from a group's member list
 * (see GroupDetailPage) rather than here, since this page has no general
 * user-search/discovery affordance in this pass's scope.
 */
export default function FriendsPage() {
  const [friends, setFriends] = useState<FriendResponse[]>([])
  const [blocks, setBlocks] = useState<BlockResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setIsLoading(true)
      setLoadError(null)
      try {
        const [friendList, blockList] = await Promise.all([listFriends(), listBlocks()])
        if (cancelled) return
        setFriends(friendList)
        setBlocks(blockList)
      } catch (err) {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : 'Could not load friends/blocks.')
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

  async function handleRemoveFriend(userId: number) {
    setActionError(null)
    try {
      await removeFriend(userId)
      setFriends((current) => current.filter((friend) => friend.userId !== userId))
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not remove friend.')
    }
  }

  async function handleUnblock(userId: number) {
    setActionError(null)
    try {
      await unblockUser(userId)
      setBlocks((current) => current.filter((block) => block.userId !== userId))
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not unblock user.')
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-2xl p-6">
        <h1 className="mb-6 text-3xl font-bold font-display text-text">Friends &amp; blocks</h1>

        {isLoading && <p className="text-sm text-text-muted">Loading…</p>}
        {loadError && <p className="text-sm text-error">{loadError}</p>}
        {actionError && (
          <p className="mb-4 rounded-lg bg-error/10 px-3 py-2 text-sm text-error">{actionError}</p>
        )}

        {!isLoading && !loadError && (
          <>
            <section className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
              <h2 className="mb-3 text-2xl font-bold font-display text-text">Friends</h2>
              {friends.length === 0 && (
                <p className="text-sm text-text-muted">
                  You haven't added any friends yet. Add friends from a group's member list.
                </p>
              )}
              <ul className="flex flex-col gap-2">
                {friends.map((friend) => (
                  <li
                    key={friend.userId}
                    className="flex items-center justify-between rounded-xl border border-border px-3 py-2"
                  >
                    <span className="text-text font-body">{friend.displayName}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveFriend(friend.userId)}
                      className="rounded-full border border-border px-3 py-1 text-sm font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                    >
                      Unfriend
                    </button>
                  </li>
                ))}
              </ul>
            </section>

            <section className="rounded-2xl bg-surface p-6 shadow-sm border border-border mb-6">
              <h2 className="mb-3 text-2xl font-bold font-display text-text">Blocked users</h2>
              {blocks.length === 0 && (
                <p className="text-sm text-text-muted">
                  You haven't blocked anyone. Block users from a group's member list.
                </p>
              )}
              <ul className="flex flex-col gap-2">
                {blocks.map((block) => (
                  <li
                    key={block.userId}
                    className="flex items-center justify-between rounded-xl border border-border px-3 py-2"
                  >
                    <span className="text-text font-body">{block.displayName}</span>
                    <button
                      type="button"
                      onClick={() => handleUnblock(block.userId)}
                      className="rounded-full border border-border px-3 py-1 text-sm font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                    >
                      Unblock
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          </>
        )}
      </div>
    </div>
  )
}
