import { useCallback, useState } from 'react'
import { useParams } from 'react-router'
import ChatPanel from '../components/ChatPanel'
import NavBar from '../components/NavBar'
import { useAuth } from '../context/AuthContext'
import { useChatPolling } from '../hooks/useChatPolling'
import { ApiError, getDirectMessages, postDirectMessage } from '../lib/apiClient'

/**
 * Direct (1:1) chat thread with another user, keyed by that user's id (not a
 * thread id) — threads are created implicitly on first message, so there's
 * no thread id to navigate by until one exists. Not friend-gated in any way:
 * this page is reachable for any other user id regardless of friend status
 * (see the "Message" link on GroupDetailPage's member list). The only way
 * sending can fail here is a generic error or, specifically, a 403 because
 * the recipient has blocked the caller — surfaced distinctly below.
 */
export default function DirectThreadPage() {
  const { userId } = useParams<{ userId: string }>()
  const otherUserId = Number(userId)
  const { user } = useAuth()

  const [sendError, setSendError] = useState<string | null>(null)

  const fetchDirectMessages = useCallback(
    (after?: number) => getDirectMessages(otherUserId, after),
    [otherUserId],
  )
  const conversationKey = Number.isFinite(otherUserId) ? otherUserId : null
  const { messages, isLoading, loadError, appendSentMessage } = useChatPolling(
    conversationKey,
    fetchDirectMessages,
  )

  // The other participant's display name is read off any message they've
  // sent so far (no separate "get user by id" endpoint is used here). Falls
  // back to a generic heading if no message from them has loaded yet (e.g. a
  // brand-new thread where only the caller has sent something, or nothing
  // has been sent at all).
  const otherDisplayName = messages.find((m) => m.senderId === otherUserId)?.senderDisplayName

  async function handleSend(body: string) {
    setSendError(null)
    try {
      const sent = await postDirectMessage(otherUserId, { body })
      appendSentMessage(sent)
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setSendError("You can't message this user.")
      } else {
        setSendError(err instanceof ApiError ? err.message : 'Could not send message.')
      }
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-2xl p-6">
        <h1 className="mb-6 text-3xl font-bold font-display text-text">{otherDisplayName ?? 'Direct message'}</h1>

        <div className="rounded-2xl bg-surface p-6 shadow-sm border border-border">
          <ChatPanel
            messages={messages}
            isLoading={isLoading}
            loadError={loadError}
            currentUserId={user?.id}
            onSend={handleSend}
            sendError={sendError}
          />
        </div>
      </div>
    </div>
  )
}
