import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import ChatPanel from '../components/ChatPanel'
import LocationPickerMap from '../components/LocationPickerMap'
import NavBar from '../components/NavBar'
import { useAuth } from '../context/AuthContext'
import { useChatPolling } from '../hooks/useChatPolling'
import { useReverseGeocode } from '../hooks/useReverseGeocode'
import {
  ApiError,
  addFriend,
  banMember,
  blockUser,
  createActivity,
  deleteGroup,
  getGroup,
  getGroupMessages,
  joinGroup,
  kickMember,
  leaveGroup,
  listActivities,
  listBlocks,
  listFriends,
  listGroupBans,
  listGroupMembers,
  postGroupMessage,
  removeFriend,
  unbanMember,
  unblockUser,
  updateGroup,
} from '../lib/apiClient'
import type {
  ActivityResponse,
  GroupBanResponse,
  GroupMemberResponse,
  GroupResponse,
} from '../lib/apiClient'

export default function GroupDetailPage() {
  const { groupId } = useParams<{ groupId: string }>()
  const id = Number(groupId)
  const navigate = useNavigate()
  const { user } = useAuth()

  const [group, setGroup] = useState<GroupResponse | null>(null)
  const [members, setMembers] = useState<GroupMemberResponse[]>([])
  const [friendIds, setFriendIds] = useState<Set<number>>(new Set())
  const [blockedIds, setBlockedIds] = useState<Set<number>>(new Set())
  const [bans, setBans] = useState<GroupBanResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [isMembershipBusy, setIsMembershipBusy] = useState(false)

  const [showBans, setShowBans] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [isSavingEdit, setIsSavingEdit] = useState(false)

  const [chatSendError, setChatSendError] = useState<string | null>(null)

  const [activities, setActivities] = useState<ActivityResponse[]>([])
  const [activitiesError, setActivitiesError] = useState<string | null>(null)
  const [isLoadingActivities, setIsLoadingActivities] = useState(false)
  const [showCreateActivity, setShowCreateActivity] = useState(false)
  const [newActivityName, setNewActivityName] = useState('')
  const [newActivityDescription, setNewActivityDescription] = useState('')
  const [newActivityScheduledTime, setNewActivityScheduledTime] = useState('')
  const [newActivityLocation, setNewActivityLocation] = useState<{ latitude: number; longitude: number } | null>(
    null,
  )
  const [isCreatingActivity, setIsCreatingActivity] = useState(false)
  const [createActivityError, setCreateActivityError] = useState<string | null>(null)

  const loadGroup = useCallback(async () => {
    const result = await getGroup(id)
    setGroup(result)
    setEditName(result.name)
    setEditDescription(result.description ?? '')
    return result
  }, [id])

  const loadMembers = useCallback(async () => {
    const result = await listGroupMembers(id)
    setMembers(result)
  }, [id])

  // Fetched once alongside the member list so each row can show "Add
  // friend"/"Unfriend" and "Block"/"Unblock" based on actual relationship
  // state, rather than always showing all four actions regardless of
  // whether they've already been taken.
  const loadRelationships = useCallback(async () => {
    const [friends, blocks] = await Promise.all([listFriends(), listBlocks()])
    setFriendIds(new Set(friends.map((friend) => friend.userId)))
    setBlockedIds(new Set(blocks.map((block) => block.userId)))
  }, [])

  const loadActivities = useCallback(async () => {
    setIsLoadingActivities(true)
    setActivitiesError(null)
    try {
      const result = await listActivities(id)
      setActivities(result)
    } catch (err) {
      setActivitiesError(err instanceof ApiError ? err.message : 'Could not load activities.')
    } finally {
      setIsLoadingActivities(false)
    }
  }, [id])

  // Chat is only polled/enabled once we know the caller is a current member
  // (group?.isMember) — passing `null` while that's not yet known/true keeps
  // the hook from firing a guaranteed-403 request, and the cleanup in
  // `useChatPolling` clears the interval whenever this key changes (e.g. on
  // leaving the group) or the page unmounts.
  const chatConversationKey = group?.isMember ? id : null
  const fetchGroupMessages = useCallback((after?: number) => getGroupMessages(id, after), [id])
  const { messages, isLoading: isChatLoading, loadError: chatLoadError, appendSentMessage } = useChatPolling(
    chatConversationKey,
    fetchGroupMessages,
  )

  async function handleSendMessage(body: string) {
    setChatSendError(null)
    try {
      const sent = await postGroupMessage(id, { body })
      appendSentMessage(sent)
    } catch (err) {
      setChatSendError(err instanceof ApiError ? err.message : 'Could not send message.')
    }
  }

  useEffect(() => {
    let cancelled = false
    async function load() {
      setIsLoading(true)
      setLoadError(null)
      try {
        const loadedGroup = await loadGroup()
        if (cancelled) return
        // Member list and activity calendar are both members-only on the
        // backend; only attempt them if the caller is currently a member
        // (avoids a guaranteed 403 for non-members just viewing a group's
        // public detail).
        if (loadedGroup.isMember) {
          await Promise.all([loadMembers(), loadActivities(), loadRelationships()])
        } else {
          setMembers([])
          setActivities([])
        }
      } catch (err) {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : 'Could not load this group.')
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [id, loadGroup, loadMembers, loadActivities, loadRelationships])

  useEffect(() => {
    if (!showBans || !group?.isAdmin) return
    let cancelled = false
    listGroupBans(id)
      .then((result) => {
        if (!cancelled) setBans(result)
      })
      .catch((err) => {
        if (!cancelled) {
          setActionError(err instanceof ApiError ? err.message : 'Could not load the ban list.')
        }
      })
    return () => {
      cancelled = true
    }
  }, [showBans, group?.isAdmin, id])

  async function refreshAfterMembershipChange() {
    const refreshed = await loadGroup()
    if (refreshed.isMember) {
      await Promise.all([loadMembers(), loadActivities(), loadRelationships()])
    } else {
      setMembers([])
      setActivities([])
    }
  }

  async function handleJoin() {
    setActionError(null)
    setIsMembershipBusy(true)
    try {
      await joinGroup(id)
      await refreshAfterMembershipChange()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not join the group.')
    } finally {
      setIsMembershipBusy(false)
    }
  }

  async function handleLeave() {
    setActionError(null)
    setIsMembershipBusy(true)
    try {
      await leaveGroup(id)
      await refreshAfterMembershipChange()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not leave the group.')
    } finally {
      setIsMembershipBusy(false)
    }
  }

  async function handleKick(userId: number) {
    setActionError(null)
    try {
      await kickMember(id, userId)
      await refreshAfterMembershipChange()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not kick this member.')
    }
  }

  async function handleBan(userId: number) {
    setActionError(null)
    try {
      await banMember(id, userId)
      await refreshAfterMembershipChange()
      if (showBans) {
        const result = await listGroupBans(id)
        setBans(result)
      }
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not ban this member.')
    }
  }

  async function handleUnban(userId: number) {
    setActionError(null)
    try {
      await unbanMember(id, userId)
      const result = await listGroupBans(id)
      setBans(result)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not unban this user.')
    }
  }

  async function handleDelete() {
    setActionError(null)
    try {
      await deleteGroup(id)
      navigate('/groups', { replace: true })
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not delete the group.')
    }
  }

  async function handleSaveEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setActionError(null)
    setIsSavingEdit(true)
    try {
      const updated = await updateGroup(id, {
        name: editName,
        description: editDescription.trim() ? editDescription : null,
      })
      setGroup(updated)
      setIsEditing(false)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not save changes.')
    } finally {
      setIsSavingEdit(false)
    }
  }

  async function handleAddFriend(userId: number) {
    setActionError(null)
    try {
      await addFriend(userId)
      setFriendIds((current) => new Set(current).add(userId))
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not add friend.')
    }
  }

  async function handleRemoveFriend(userId: number) {
    setActionError(null)
    try {
      await removeFriend(userId)
      setFriendIds((current) => {
        const next = new Set(current)
        next.delete(userId)
        return next
      })
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not remove friend.')
    }
  }

  async function handleBlock(userId: number) {
    setActionError(null)
    try {
      await blockUser(userId)
      setBlockedIds((current) => new Set(current).add(userId))
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not block user.')
    }
  }

  async function handleUnblock(userId: number) {
    setActionError(null)
    try {
      await unblockUser(userId)
      setBlockedIds((current) => {
        const next = new Set(current)
        next.delete(userId)
        return next
      })
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not unblock user.')
    }
  }

  function resetCreateActivityForm() {
    setNewActivityName('')
    setNewActivityDescription('')
    setNewActivityScheduledTime('')
    setNewActivityLocation(null)
    setCreateActivityError(null)
  }

  async function handleCreateActivity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCreateActivityError(null)
    setIsCreatingActivity(true)
    try {
      await createActivity(id, {
        name: newActivityName,
        description: newActivityDescription.trim() ? newActivityDescription : null,
        scheduledTime: fromDateTimeInputValue(newActivityScheduledTime),
        latitude: newActivityLocation?.latitude ?? null,
        longitude: newActivityLocation?.longitude ?? null,
      })
      resetCreateActivityForm()
      setShowCreateActivity(false)
      await loadActivities()
    } catch (err) {
      setCreateActivityError(err instanceof ApiError ? err.message : 'Could not create the activity.')
    } finally {
      setIsCreatingActivity(false)
    }
  }

  // Reverse-geocode the create form's selected location so the user sees a
  // place name instead of raw coordinates.
  const newPickerCoords = newActivityLocation
    ? { lat: newActivityLocation.latitude, lng: newActivityLocation.longitude }
    : null
  const {
    placeName: newPlaceName,
    loading: newGeocoding,
    error: newGeocodeError,
  } = useReverseGeocode(newPickerCoords)

  if (isLoading) {
    return (
      <div className="min-h-screen bg-background">
        <NavBar />
        <p className="p-6 text-text-muted">Loading…</p>
      </div>
    )
  }

  if (loadError || !group) {
    return (
      <div className="min-h-screen bg-background">
        <NavBar />
        <p className="p-6 text-error">{loadError ?? 'Group not found.'}</p>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-3xl p-6">
        {actionError && (
          <p className="mb-4 rounded-lg bg-error/10 px-3 py-2 text-sm text-error">{actionError}</p>
        )}

        <div className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
          {!isEditing ? (
            <>
              <div className="flex items-start justify-between">
                <div>
                  <div className="mb-3 flex flex-wrap gap-2">
                    {group.categories.map((category) => (
                      <span
                        key={category.id}
                        className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary"
                      >
                        {category.name}
                      </span>
                    ))}
                  </div>
                  <h1 className="text-3xl font-bold font-display text-text">{group.name}</h1>
                  {group.description && <p className="mt-2 text-text-muted font-body">{group.description}</p>}
                </div>
                {group.isMember ? (
                  <button
                    type="button"
                    onClick={handleLeave}
                    disabled={isMembershipBusy}
                    className="rounded-full border border-error/30 px-4 py-2 font-medium text-error transition-colors motion-safe:hover:bg-error/5 focus:outline-none focus:ring-2 focus:ring-error focus:ring-offset-2 disabled:opacity-50"
                  >
                    Leave
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleJoin}
                    disabled={isMembershipBusy}
                    className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
                  >
                    Join
                  </button>
                )}
              </div>
              <p className="mt-3 text-sm text-text-muted font-body">
                {group.memberCount} member{group.memberCount === 1 ? '' : 's'}
              </p>
              {group.isAdmin && (
                <button
                  type="button"
                  onClick={() => setIsEditing(true)}
                  className="mt-4 rounded-full border border-border px-3 py-1.5 text-sm font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                >
                  Edit name / description
                </button>
              )}
            </>
          ) : (
            <form onSubmit={handleSaveEdit}>
              <label className="mb-3 block">
                <span className="mb-1 block text-sm font-medium font-body text-text-muted">Name</span>
                <input
                  type="text"
                  required
                  maxLength={200}
                  value={editName}
                  onChange={(event) => setEditName(event.target.value)}
                  className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </label>
              <label className="mb-4 block">
                <span className="mb-1 block text-sm font-medium font-body text-text-muted">Description</span>
                <textarea
                  maxLength={2000}
                  rows={3}
                  value={editDescription}
                  onChange={(event) => setEditDescription(event.target.value)}
                  className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </label>
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={isSavingEdit}
                  className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
                >
                  {isSavingEdit ? 'Saving…' : 'Save'}
                </button>
                <button
                  type="button"
                  onClick={() => setIsEditing(false)}
                  className="rounded-full border border-border px-4 py-2 font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>

        {group.isMember && (
          <div className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
            <h2 className="text-2xl font-bold font-display text-text mb-3">Members</h2>
            <ul className="flex flex-col gap-2">
              {members.map((member) => (
                <MemberRow
                  key={member.userId}
                  member={member}
                  isSelf={member.userId === user?.id}
                  isCallerAdmin={group.isAdmin}
                  isFriend={friendIds.has(member.userId)}
                  isBlocked={blockedIds.has(member.userId)}
                  onKick={() => handleKick(member.userId)}
                  onBan={() => handleBan(member.userId)}
                  onAddFriend={() => handleAddFriend(member.userId)}
                  onRemoveFriend={() => handleRemoveFriend(member.userId)}
                  onBlock={() => handleBlock(member.userId)}
                  onUnblock={() => handleUnblock(member.userId)}
                />
              ))}
            </ul>
          </div>
        )}

        {group.isAdmin && (
          <div className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
            <h2 className="text-2xl font-bold font-display text-text mb-3">Admin controls</h2>

            <button
              type="button"
              onClick={() => setShowBans((current) => !current)}
              className="mb-3 rounded-full border border-border px-3 py-1.5 text-sm font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            >
              {showBans ? 'Hide ban list' : 'Show ban list'}
            </button>

            {showBans && (
              <ul className="mb-4 flex flex-col gap-2">
                {bans.length === 0 && <p className="text-sm text-text-muted">No banned users.</p>}
                {bans.map((ban) => (
                  <li
                    key={ban.userId}
                    className="flex items-center justify-between rounded-xl border border-border px-3 py-2"
                  >
                    <span className="text-text font-body">{ban.displayName}</span>
                    <button
                      type="button"
                      onClick={() => handleUnban(ban.userId)}
                      className="rounded-full border border-border px-3 py-1.5 text-sm font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                    >
                      Unban
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <div className="border-t border-border pt-4">
              {!showDeleteConfirm ? (
                <button
                  type="button"
                  onClick={() => setShowDeleteConfirm(true)}
                  className="rounded-full border border-error/30 px-4 py-2 font-medium text-error transition-colors motion-safe:hover:bg-error/5 focus:outline-none focus:ring-2 focus:ring-error focus:ring-offset-2"
                >
                  Delete group
                </button>
              ) : (
                <div className="flex items-center gap-3">
                  <span className="text-sm text-error">Delete this group permanently?</span>
                  <button
                    type="button"
                    onClick={handleDelete}
                    className="rounded-full border border-error/30 px-4 py-2 font-medium text-error transition-colors motion-safe:hover:bg-error/5 focus:outline-none focus:ring-2 focus:ring-error focus:ring-offset-2"
                  >
                    Yes, delete
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowDeleteConfirm(false)}
                    className="rounded-full border border-border px-4 py-2 font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                  >
                    Cancel
                  </button>
                </div>
              )}
            </div>
          </div>
        )}

        {group.isMember && (
          <div className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
            <h2 className="text-2xl font-bold font-display text-text mb-3">Group chat</h2>
            <ChatPanel
              messages={messages}
              isLoading={isChatLoading}
              loadError={chatLoadError}
              currentUserId={user?.id}
              onSend={handleSendMessage}
              sendError={chatSendError}
            />
          </div>
        )}

        {group.isMember && (
          <div className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-2xl font-bold font-display text-text">Activities</h2>
              <button
                type="button"
                onClick={() => {
                  if (showCreateActivity) {
                    // Closing the form — reset location so the map mounts fresh next time
                    setNewActivityLocation(null)
                  }
                  setShowCreateActivity((current) => !current)
                }}
                className="rounded-full bg-primary px-3 py-1.5 text-sm font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
              >
                {showCreateActivity ? 'Cancel' : 'Create activity'}
              </button>
            </div>

            {showCreateActivity && (
              <form
                onSubmit={handleCreateActivity}
                className="mb-4 rounded-xl border border-border bg-background p-4"
              >
                <label className="mb-3 block">
                  <span className="mb-1 block text-sm font-medium font-body text-text-muted">Name</span>
                  <input
                    type="text"
                    required
                    maxLength={200}
                    value={newActivityName}
                    onChange={(event) => setNewActivityName(event.target.value)}
                    className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </label>

                <label className="mb-3 block">
                  <span className="mb-1 block text-sm font-medium font-body text-text-muted">Description</span>
                  <textarea
                    maxLength={2000}
                    rows={3}
                    value={newActivityDescription}
                    onChange={(event) => setNewActivityDescription(event.target.value)}
                    className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </label>

                <label className="mb-3 block">
                  <span className="mb-1 block text-sm font-medium font-body text-text-muted">Scheduled time</span>
                  <input
                    type="datetime-local"
                    required
                    value={newActivityScheduledTime}
                    onChange={(event) => setNewActivityScheduledTime(event.target.value)}
                    className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </label>

                <div className="mb-4">
                  <span className="mb-1 block text-sm font-medium font-body text-text-muted">
                    Location (optional)
                  </span>

                  <LocationPickerMap
                    initialPosition={null}
                    onChange={(pos) =>
                      setNewActivityLocation({ latitude: pos.lat, longitude: pos.lng })
                    }
                    className="h-48 w-full rounded-xl"
                  />

                  {newActivityLocation ? (
                    <div className="mt-2 flex items-center gap-2">
                      {newGeocoding ? (
                        <span className="text-sm font-body text-text-muted italic">
                          Resolving location…
                        </span>
                      ) : newGeocodeError ? (
                        <span className="text-sm font-body text-text-muted">
                          Location selected (place name unavailable)
                        </span>
                      ) : (
                        <span className="rounded-full bg-primary/10 px-3 py-1 text-sm font-medium font-body text-primary">
                          {newPlaceName}
                        </span>
                      )}
                      <button
                        type="button"
                        onClick={() => setNewActivityLocation(null)}
                        className="rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                      >
                        Remove
                      </button>
                    </div>
                  ) : (
                    <p className="mt-1 text-xs text-text-muted font-body">
                      A location isn't required — only attach one if this activity is
                      happening somewhere specific.
                    </p>
                  )}
                </div>

                {createActivityError && (
                  <p className="mb-3 text-sm text-error">{createActivityError}</p>
                )}

                <button
                  type="submit"
                  disabled={isCreatingActivity}
                  className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
                >
                  {isCreatingActivity ? 'Creating…' : 'Create activity'}
                </button>
              </form>
            )}

            {isLoadingActivities && <p className="text-sm text-text-muted">Loading activities…</p>}
            {activitiesError && <p className="text-sm text-error">{activitiesError}</p>}
            {!isLoadingActivities && !activitiesError && activities.length === 0 && (
              <p className="text-sm text-text-muted">No activities scheduled yet — be the first to plan something.</p>
            )}
            {!isLoadingActivities && !activitiesError && activities.length > 0 && (
              <ul className="flex flex-col gap-2">
                {activities.map((activity) => (
                  <ActivityCard key={activity.id} groupId={id} activity={activity} />
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/** A `datetime-local` input's local-time value -> an ISO `Instant` string for the backend. */
function fromDateTimeInputValue(value: string): string {
  return new Date(value).toISOString()
}

/**
 * Single activity card rendered in the group's activity list.
 * Defined at module scope so React doesn't recreate the component type on
 * every render of GroupDetailPage (which would remount all cards).
 * Calls useReverseGeocode to show the place name instead of "Has a location".
 */
function ActivityCard({
  groupId,
  activity,
}: {
  groupId: number
  activity: ActivityResponse
}) {
  const coords =
    activity.latitude !== null && activity.longitude !== null
      ? { lat: activity.latitude, lng: activity.longitude }
      : null
  const { placeName, loading, error } = useReverseGeocode(coords)

  return (
    <li>
      <Link
        to={`/groups/${groupId}/activities/${activity.id}`}
        className="block rounded-xl border border-border px-4 py-3 transition-colors motion-safe:hover:border-primary/60"
      >
        <div className="flex items-center justify-between">
          <span className="font-medium text-text font-body">{activity.name}</span>
          <span className="text-sm text-text-muted font-body">
            {new Date(activity.scheduledTime).toLocaleString()}
          </span>
        </div>
        {activity.description && (
          <p className="mt-1 line-clamp-2 text-sm text-text-muted font-body">
            {activity.description}
          </p>
        )}
        {coords && (
          <p className="mt-1 text-xs text-text-muted font-body">
            {loading
              ? 'Resolving location…'
              : error
                ? 'Location (place name unavailable)'
                : placeName}
          </p>
        )}
      </Link>
    </li>
  )
}

function MemberRow({
  member,
  isSelf,
  isCallerAdmin,
  isFriend,
  isBlocked,
  onKick,
  onBan,
  onAddFriend,
  onRemoveFriend,
  onBlock,
  onUnblock,
}: {
  member: GroupMemberResponse
  isSelf: boolean
  isCallerAdmin: boolean
  isFriend: boolean
  isBlocked: boolean
  onKick: () => void
  onBan: () => void
  onAddFriend: () => void
  onRemoveFriend: () => void
  onBlock: () => void
  onUnblock: () => void
}) {
  return (
    <li className="flex items-center justify-between rounded-xl border border-border px-3 py-2">
      <span className="text-text font-body">
        {member.displayName}
        {member.isAdmin && (
          <span className="ml-2 rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">admin</span>
        )}
      </span>
      {!isSelf && (
        <div className="flex flex-wrap gap-2">
          <Link
            to={`/messages/${member.userId}`}
            className="rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Message
          </Link>
          {isFriend ? (
            <button
              type="button"
              onClick={onRemoveFriend}
              className="rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            >
              Unfriend
            </button>
          ) : (
            <button
              type="button"
              onClick={onAddFriend}
              className="rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            >
              Add friend
            </button>
          )}
          {isBlocked ? (
            <button
              type="button"
              onClick={onUnblock}
              className="rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            >
              Unblock
            </button>
          ) : (
            <button
              type="button"
              onClick={onBlock}
              className="rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            >
              Block
            </button>
          )}
          {isCallerAdmin && (
            <>
              <button
                type="button"
                onClick={onKick}
                className="rounded-full border border-accent/30 px-2 py-1 text-xs font-medium text-accent transition-colors motion-safe:hover:bg-accent/5 focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2"
              >
                Kick
              </button>
              <button
                type="button"
                onClick={onBan}
                className="rounded-full border border-error/30 px-2 py-1 text-xs font-medium text-error transition-colors motion-safe:hover:bg-error/5 focus:outline-none focus:ring-2 focus:ring-error focus:ring-offset-2"
              >
                Ban
              </button>
            </>
          )}
        </div>
      )}
    </li>
  )
}
