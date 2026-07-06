import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../lib/apiClient'

const POLL_INTERVAL_MS = 4000

/** Common shape both `GroupChatMessageResponse` and `DirectMessageResponse` satisfy. */
interface PollableMessage {
  id: number
}

/**
 * Shared cursor-based polling logic for group chat and direct chat, which
 * both follow the identical `GET .../messages?after=<lastId>` pattern (see
 * design.md §5 and the `GroupChatService`/`DirectChatService` javadoc for the
 * id-cursor rationale). Loads the initial page on mount (and whenever
 * `conversationKey` changes, e.g. navigating from one group/DM to another),
 * then polls on a `setInterval` for messages newer than the last one seen,
 * appending them rather than re-fetching the whole history. The interval is
 * cleared on unmount/key-change — the one thing this hook exists to get
 * right, since a leaked interval would mean duplicate polling requests
 * piling up (see GroupDetailPage/DM thread page callers).
 *
 * `conversationKey` should be a stable primitive (group id, other-user id)
 * that identifies which conversation to poll; passing `null` disables
 * loading/polling entirely (e.g. while the caller isn't yet known to be a
 * member, or auth is still loading).
 */
export function useChatPolling<T extends PollableMessage>(
  conversationKey: number | null,
  fetchMessages: (after?: number) => Promise<T[]>,
) {
  const [messages, setMessages] = useState<T[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  // Tracks the last message id seen, for the next `after` poll. A ref (not
  // state) so the interval callback always reads the latest value without
  // needing to be re-created on every new message.
  const lastIdRef = useRef<number | null>(null)
  // Re-entrancy guard: skip a poll tick if the previous one hasn't resolved
  // yet (e.g. a slow/cold-started Render dyno), so polls never overlap.
  const isPollingRef = useRef(false)

  // Keep the latest fetcher in a ref so the interval effect below doesn't
  // need `fetchMessages` in its dependency array (it's a new function
  // identity on every render for inline callers).
  const fetchMessagesRef = useRef(fetchMessages)
  fetchMessagesRef.current = fetchMessages

  useEffect(() => {
    if (conversationKey === null) {
      setMessages([])
      setIsLoading(false)
      return
    }

    let cancelled = false
    lastIdRef.current = null
    setMessages([])
    setIsLoading(true)
    setLoadError(null)

    fetchMessagesRef
      .current()
      .then((initial) => {
        if (cancelled) return
        setMessages(initial)
        if (initial.length > 0) {
          lastIdRef.current = initial[initial.length - 1].id
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : 'Could not load messages.')
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    const intervalId = window.setInterval(() => {
      if (isPollingRef.current) return
      isPollingRef.current = true
      fetchMessagesRef
        .current(lastIdRef.current ?? undefined)
        .then((fresh) => {
          if (cancelled || fresh.length === 0) return
          // De-dupe against what's already rendered: if the user sent a
          // message while this poll was in flight, `appendSentMessage` may
          // have already added it (optimistically) before this response
          // arrived, since the backend can see the new message before the
          // poll resolves (especially on a slow/cold-started Render dyno).
          // Without this check that message would render twice under the
          // same backend-issued id. The cursor (`lastIdRef`) still advances
          // to the latest id seen from the backend regardless of whether a
          // given message was newly appended, so the next poll's `after`
          // stays correct.
          setMessages((current) => {
            const seenIds = new Set(current.map((message) => message.id))
            const deduped = fresh.filter((message) => !seenIds.has(message.id))
            return deduped.length > 0 ? [...current, ...deduped] : current
          })
          lastIdRef.current = fresh[fresh.length - 1].id
        })
        .catch(() => {
          // Transient polling errors are swallowed (not surfaced as
          // `loadError`) so a single slow/failed poll tick doesn't blank out
          // an otherwise-working chat view; the next tick simply retries.
        })
        .finally(() => {
          isPollingRef.current = false
        })
    }, POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [conversationKey])

  const appendSentMessage = useCallback((message: T) => {
    // Same de-dupe as the poll-tick handler above, for the opposite race: a
    // poll already in flight when the message was sent can resolve with
    // that same backend-issued id after this optimistic append runs.
    setMessages((current) => (current.some((m) => m.id === message.id) ? current : [...current, message]))
    lastIdRef.current = message.id
  }, [])

  return { messages, isLoading, loadError, appendSentMessage }
}
