import { useState } from 'react'
import type { FormEvent } from 'react'

export interface ChatPanelMessage {
  id: number
  senderId: number
  senderDisplayName: string | null
  body: string
  createdAt: string
}

/**
 * Shared message-list + composer UI for both group chat (`GroupDetailPage`)
 * and direct chat (`DirectThreadPage`) — both poll for new messages via
 * `useChatPolling` and pass the resulting list down here. Renders
 * oldest-at-top, newest-at-bottom (chronological reading order, consistent
 * with how the backend already returns pages — see `apiClient.ts`'s
 * `getGroupMessages`/`getDirectMessages` docs).
 */
export default function ChatPanel({
  messages,
  isLoading,
  loadError,
  currentUserId,
  onSend,
  sendError,
  isGroupAdmin = false,
  onDelete,
}: {
  messages: ChatPanelMessage[]
  isLoading: boolean
  loadError: string | null
  currentUserId: number | undefined
  onSend: (body: string) => Promise<void>
  sendError: string | null
  /** Group chat only — omit for direct messages, where there's no admin concept. */
  isGroupAdmin?: boolean
  /** When provided, shows a delete affordance for messages the viewer may remove. */
  onDelete?: (messageId: number) => Promise<void>
}) {
  const [draft, setDraft] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  async function handleDelete(messageId: number) {
    if (!onDelete || deletingId !== null) return
    setDeletingId(messageId)
    try {
      await onDelete(messageId)
    } finally {
      setDeletingId(null)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = draft.trim()
    if (!body || isSending) return
    setIsSending(true)
    try {
      await onSend(body)
      setDraft('')
    } finally {
      setIsSending(false)
    }
  }

  return (
    <div className="flex flex-col">
      {loadError && <p className="mb-3 text-sm text-error">{loadError}</p>}

      <div className="mb-3 flex h-72 flex-col gap-2 overflow-y-auto rounded-xl border border-border bg-background p-3">
        {isLoading && <p className="text-sm text-text-muted">Loading messages…</p>}
        {!isLoading && messages.length === 0 && !loadError && (
          <p className="text-sm text-text-muted">No messages yet. Say hello!</p>
        )}
        {messages.map((message) => {
          const isOwn = message.senderId === currentUserId
          const canDelete = Boolean(onDelete) && (isOwn || isGroupAdmin)
          return (
            <div key={message.id} className={`flex flex-col ${isOwn ? 'items-end' : 'items-start'}`}>
              <div
                className={`max-w-4/5 rounded-2xl px-3 py-2 text-sm ${
                  isOwn ? 'bg-primary text-on-primary' : 'bg-surface text-text shadow-sm ring-1 ring-border'
                }`}
              >
                {!isOwn && (
                  <p className="mb-0.5 text-xs font-semibold text-text-muted">
                    {message.senderDisplayName ?? 'Unknown user'}
                  </p>
                )}
                <p className="whitespace-pre-wrap break-words">{message.body}</p>
              </div>
              <span className="mt-0.5 flex items-center gap-2 text-xs text-text-muted">
                {new Date(message.createdAt).toLocaleString()}
                {canDelete && (
                  <button
                    type="button"
                    onClick={() => handleDelete(message.id)}
                    disabled={deletingId === message.id}
                    className="font-medium text-error transition-colors hover:underline disabled:opacity-50"
                  >
                    {deletingId === message.id ? 'Deleting…' : 'Delete'}
                  </button>
                )}
              </span>
            </div>
          )
        })}
      </div>

      {sendError && <p className="mb-2 text-sm text-error">{sendError}</p>}

      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          maxLength={4000}
          placeholder="Type a message…"
          className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        />
        <button
          type="submit"
          disabled={isSending || !draft.trim()}
          className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
        >
          {isSending ? 'Sending…' : 'Send'}
        </button>
      </form>
    </div>
  )
}
