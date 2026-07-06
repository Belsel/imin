---
status: implemented
owner:
created: 2026-07-06
---

# Group Chat Message Moderation

## Problem

Group admins can already edit/delete any activity in their group (see
`specs/mvp/spec.md` Activities), but have no equivalent power over group
chat messages — there is no delete endpoint, service method, or UI for it
at all. Admins have no way to remove abusive/spam messages short of
banning the sender outright.

## Requirements

- A `DELETE /api/groups/{groupId}/messages/{messageId}` endpoint.
- Authorization mirrors `ActivityService.requireOwnerOrAdmin`: the
  message's own sender, or any current admin of the group, may delete it.
  A non-sender, non-admin member gets 403.
- Deleting a message removes the row outright (hard delete, consistent
  with activity delete — no soft-delete/tombstone concept exists anywhere
  else in this codebase).
- Frontend: a delete affordance on each message in `ChatPanel`, shown only
  when the viewer is the sender or the group's admin (mirrors how
  `ActivityDetailPage` gates its Edit/Delete buttons on `group.isAdmin`).

## Out of scope

- Editing message content (no edit capability for activities' analogous
  case either — only delete).
- Any notice/audit trail of "this message was deleted by an admin."
- Soft-delete/undo.

## Acceptance criteria

- [x] The sender of a message can delete their own message.
- [x] Any current admin of the group can delete any message in that
      group's chat, including ones they didn't send.
- [x] A non-sender, non-admin member gets 403 attempting to delete.
- [x] A deleted message no longer appears in subsequent
      `GET .../messages` polls (initial page or `after` cursor).
- [x] Deleting a message that doesn't belong to the given group, or
      doesn't exist, returns 404.

## Design notes

Follows `ActivityController`/`ActivityService`'s exact shape:
`GroupChatController` gets a `@DeleteMapping("/{messageId}")` sibling to
its existing `GET`/`POST`, delegating to a new
`GroupChatService.deleteMessage(callerEmail, groupId, messageId)` that
reuses the same `requireOwnerOrAdmin`-equivalent check pattern already
proven in `ActivityService`. No new repository query needed beyond
`GroupChatMessageRepository.findById` (already implied by JpaRepository)
plus a `deleteById`/`delete` call.

Frontend: `ChatPanel` needs the caller's own user id (for the sender
check) and the group's `isAdmin` flag (already fetched by
`GroupDetailPage` and passed to `ChatPanel` as `currentUserId` — check
whether `isAdmin` is already threaded through or needs adding as a prop).

## Implementation notes

- Backend: `GroupChatController` gained `DELETE /{messageId}`;
  `GroupChatService.deleteMessage` mirrors `ActivityService`'s
  owner-or-admin check exactly (`requireSenderOrAdmin`/`findMessageOr404`).
- Frontend: `ChatPanel` gained optional `isGroupAdmin`/`onDelete` props
  (both omitted by `DirectThreadPage`, so direct messages are unaffected).
  `useChatPolling` gained a `removeMessage(id)` companion to
  `appendSentMessage`, since a hard-deleted message has no tombstone for a
  poll to discover — the caller drops it from local state directly on a
  successful delete. `GroupDetailPage` wires `group.isAdmin` and a
  `handleDeleteMessage` handler through.

## Verification

Added to `GroupChatServiceTest`: sender-can-delete-own-message,
admin-can-delete-anothers, non-sender-non-admin-gets-403 (message still
present after the rejected attempt), and delete-returns-404 for a wrong
group or nonexistent message id. Full backend suite (20 test classes) run
afterward — all green, no regressions. Frontend type-checked clean after
each stage of the change (`tsc --noEmit`).
