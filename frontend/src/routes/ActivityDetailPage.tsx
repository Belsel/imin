import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import LocationPickerMap from '../components/LocationPickerMap'
import NavBar from '../components/NavBar'
import RoutingControl from '../components/RoutingControl'
import { useAuth } from '../context/AuthContext'
import { useReverseGeocode } from '../hooks/useReverseGeocode'
import { getCurrentPosition } from '../lib/geolocation'
import {
  ApiError,
  deleteActivity,
  getActivity,
  getDirections,
  getGroup,
  updateActivity,
} from '../lib/apiClient'
import type { ActivityResponse, GroupResponse, RouteResponse } from '../lib/apiClient'

/**
 * Full detail/edit view for a single activity. Reachable from
 * `GroupDetailPage`'s activity calendar list. Shows name/description/
 * scheduled time/location; if a location is present, renders it on a map
 * (`RoutingControl`) with a "Get directions" affordance (spec.md Maps/
 * Routing: any map showing a specific target location must offer real
 * turn-by-turn routing to it).
 *
 * Edit/delete controls are only rendered for the activity's owner or a
 * current admin of the activity's group — the backend already enforces this
 * (403 otherwise), but the UI only shows the controls to users who'd
 * actually be allowed, consistent with how `GroupDetailPage` gates its own
 * admin controls on `group.isAdmin`.
 */
export default function ActivityDetailPage() {
  const { groupId, activityId } = useParams<{ groupId: string; activityId: string }>()
  const gId = Number(groupId)
  const aId = Number(activityId)
  const navigate = useNavigate()
  const { user } = useAuth()

  const [group, setGroup] = useState<GroupResponse | null>(null)
  const [activity, setActivity] = useState<ActivityResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editScheduledTime, setEditScheduledTime] = useState('')
  const [editLocation, setEditLocation] = useState<{ latitude: number; longitude: number } | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const [origin, setOrigin] = useState<{ lat: number; lng: number } | null>(null)
  const [route, setRoute] = useState<RouteResponse | null>(null)
  const [isRouting, setIsRouting] = useState(false)
  const [routingError, setRoutingError] = useState<string | null>(null)

  const loadAll = useCallback(async () => {
    const [loadedGroup, loadedActivity] = await Promise.all([getGroup(gId), getActivity(gId, aId)])
    setGroup(loadedGroup)
    setActivity(loadedActivity)
    setEditName(loadedActivity.name)
    setEditDescription(loadedActivity.description ?? '')
    setEditScheduledTime(toDateTimeInputValue(loadedActivity.scheduledTime))
    setEditLocation(
      loadedActivity.latitude !== null && loadedActivity.longitude !== null
        ? { latitude: loadedActivity.latitude, longitude: loadedActivity.longitude }
        : null,
    )
  }, [gId, aId])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setIsLoading(true)
      setLoadError(null)
      try {
        await loadAll()
      } catch (err) {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : 'Could not load this activity.')
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [loadAll])

  const canEdit = Boolean(activity && group && (activity.ownerId === user?.id || group.isAdmin))

  async function handleSaveEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setActionError(null)
    setIsSaving(true)
    try {
      const updated = await updateActivity(gId, aId, {
        name: editName,
        description: editDescription.trim() ? editDescription : null,
        scheduledTime: fromDateTimeInputValue(editScheduledTime),
        latitude: editLocation?.latitude ?? null,
        longitude: editLocation?.longitude ?? null,
      })
      setActivity(updated)
      setIsEditing(false)
      // Location may have changed — clear any previously-computed route
      // rather than show a stale route for the old location.
      setRoute(null)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not save changes.')
    } finally {
      setIsSaving(false)
    }
  }

  async function handleDelete() {
    setActionError(null)
    try {
      await deleteActivity(gId, aId)
      navigate(`/groups/${gId}`, { replace: true })
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not delete this activity.')
    }
  }

  async function handleGetDirections() {
    if (!activity || activity.latitude === null || activity.longitude === null) return
    setRoutingError(null)
    setIsRouting(true)
    try {
      const position = await getCurrentPosition()
      setOrigin({ lat: position.latitude, lng: position.longitude })
      const result = await getDirections(
        position.latitude,
        position.longitude,
        activity.latitude,
        activity.longitude,
      )
      setRoute(result)
    } catch (err) {
      setRoute(null)
      setRoutingError(
        err instanceof ApiError
          ? `Could not get directions: ${err.message}`
          : err instanceof Error
            ? err.message
            : 'Could not get directions.',
      )
    } finally {
      setIsRouting(false)
    }
  }

  // Reverse-geocode for the view-mode location section (R7).
  // Checks `activity` is loaded first — `activity?.latitude !== null` is not
  // enough on its own, since `undefined !== null` is also true, which let
  // this run (and crash on the `activity!` assertion) before `activity` loaded.
  const viewCoords =
    activity && activity.latitude !== null && activity.longitude !== null
      ? { lat: activity.latitude, lng: activity.longitude }
      : null
  const {
    placeName: viewPlaceName,
    loading: viewGeocoding,
    error: viewGeocodeError,
  } = useReverseGeocode(viewCoords)

  // Reverse-geocode for the edit-form location section (R3).
  const editPickerCoords = editLocation
    ? { lat: editLocation.latitude, lng: editLocation.longitude }
    : null
  const {
    placeName: editPlaceName,
    loading: editGeocoding,
    error: editGeocodeError,
  } = useReverseGeocode(editPickerCoords)

  if (isLoading) {
    return (
      <div className="min-h-screen bg-background">
        <NavBar />
        <p className="p-6 text-text-muted">Loading…</p>
      </div>
    )
  }

  if (loadError || !activity || !group) {
    return (
      <div className="min-h-screen bg-background">
        <NavBar />
        <p className="p-6 text-error">{loadError ?? 'Activity not found.'}</p>
      </div>
    )
  }

  const hasLocation = activity.latitude !== null && activity.longitude !== null

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-3xl p-6">
        <button
          type="button"
          onClick={() => navigate(`/groups/${gId}`)}
          className="text-sm font-medium font-body text-primary motion-safe:hover:underline mb-4 inline-block"
        >
          ← Back to {group.name}
        </button>

        {actionError && (
          <p className="mb-4 rounded-lg bg-error/10 px-3 py-2 text-sm text-error">{actionError}</p>
        )}

        <div className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
          {!isEditing ? (
            <>
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-xs font-semibold font-body tracking-[0.2em] uppercase text-primary mb-1">{group.name}</p>
                  <h1 className="text-3xl font-bold font-display text-text">{activity.name}</h1>
                  <p className="mt-1 text-sm text-text-muted font-body">
                    {new Date(activity.scheduledTime).toLocaleString()}
                  </p>
                </div>
                {canEdit && (
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => setIsEditing(true)}
                      className="rounded-full border border-border px-3 py-1.5 text-sm font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                    >
                      Edit
                    </button>
                    {!showDeleteConfirm ? (
                      <button
                        type="button"
                        onClick={() => setShowDeleteConfirm(true)}
                        className="rounded-full border border-error/30 px-3 py-1.5 text-sm font-medium text-error transition-colors motion-safe:hover:bg-error/5 focus:outline-none focus:ring-2 focus:ring-error focus:ring-offset-2"
                      >
                        Delete
                      </button>
                    ) : (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={handleDelete}
                          className="rounded-full border border-error/30 px-3 py-1.5 text-sm font-medium text-error transition-colors motion-safe:hover:bg-error/5 focus:outline-none focus:ring-2 focus:ring-error focus:ring-offset-2"
                        >
                          Yes, delete
                        </button>
                        <button
                          type="button"
                          onClick={() => setShowDeleteConfirm(false)}
                          className="rounded-full border border-border px-3 py-1.5 text-sm font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                        >
                          Cancel
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {activity.description && (
                <p className="mt-4 whitespace-pre-wrap text-text font-body">{activity.description}</p>
              )}

              <p className="mt-4 text-sm text-text-muted font-body">
                Created by {activity.ownerDisplayName ?? 'Unknown user'}
              </p>
            </>
          ) : (
            <form onSubmit={handleSaveEdit}>
              <label className="mb-4 block">
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
                  rows={4}
                  value={editDescription}
                  onChange={(event) => setEditDescription(event.target.value)}
                  className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </label>

              <label className="mb-4 block">
                <span className="mb-1 block text-sm font-medium font-body text-text-muted">Scheduled time</span>
                <input
                  type="datetime-local"
                  required
                  value={editScheduledTime}
                  onChange={(event) => setEditScheduledTime(event.target.value)}
                  className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </label>

              <div className="mb-4">
                <span className="mb-1 block text-sm font-medium font-body text-text-muted">
                  Location (optional)
                </span>

                <LocationPickerMap
                  initialPosition={editPickerCoords}
                  onChange={(pos) =>
                    setEditLocation({ latitude: pos.lat, longitude: pos.lng })
                  }
                  className="h-48 w-full rounded-xl"
                />

                {editLocation ? (
                  <div className="mt-2 flex items-center gap-2">
                    {editGeocoding ? (
                      <span className="text-sm font-body text-text-muted italic">
                        Resolving location…
                      </span>
                    ) : editGeocodeError ? (
                      <span className="text-sm font-body text-text-muted">
                        Location selected (place name unavailable)
                      </span>
                    ) : (
                      <span className="rounded-full bg-primary/10 px-3 py-1 text-sm font-medium font-body text-primary">
                        {editPlaceName}
                      </span>
                    )}
                    <button
                      type="button"
                      onClick={() => setEditLocation(null)}
                      className="rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                    >
                      Remove
                    </button>
                  </div>
                ) : null}
              </div>

              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={isSaving}
                  className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
                >
                  {isSaving ? 'Saving…' : 'Save'}
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

        {hasLocation && (
          <div className="rounded-2xl bg-surface p-6 shadow-sm border border-border">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-2xl font-bold font-display text-text">Location</h2>
              <button
                type="button"
                onClick={handleGetDirections}
                disabled={isRouting}
                className="rounded-full bg-primary px-3 py-1.5 text-sm font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
              >
                {isRouting ? 'Getting directions…' : 'Get directions'}
              </button>
            </div>

            {/* Place name — sits between heading and map */}
            <p className="mb-3 text-sm font-body text-text-muted">
              {viewGeocoding
                ? 'Resolving location…'
                : viewGeocodeError
                  ? 'Location (place name unavailable)'
                  : viewPlaceName}
            </p>

            {routingError && <p className="mb-3 text-sm text-error">{routingError}</p>}

            <RoutingControl
              target={{ lat: activity.latitude as number, lng: activity.longitude as number }}
              targetLabel={activity.name}
              origin={origin}
              route={route}
              className="h-64 w-full rounded-2xl overflow-hidden"
            />

            {route && (
              <div className="mt-4">
                <p className="mb-2 text-sm font-medium text-text font-body">
                  {(route.distanceMeters / 1000).toFixed(1)} km · {formatDuration(route.durationSeconds)}
                </p>
                {route.steps.length > 0 && (
                  <ol className="flex flex-col gap-1 text-sm text-text">
                    {route.steps.map((step, index) => (
                      <li key={index} className="rounded-xl border border-border px-3 py-2">
                        <span className="font-medium text-text">{index + 1}.</span> {step.instruction}
                        <span className="ml-2 text-xs text-text-muted">
                          ({(step.distanceMeters / 1000).toFixed(2)} km)
                        </span>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function formatDuration(seconds: number): string {
  const totalMinutes = Math.round(seconds / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours === 0) return `${minutes} min`
  return `${hours} h ${minutes} min`
}

/** `Instant` ISO string -> the local-time value a `datetime-local` input expects (`YYYY-MM-DDTHH:mm`). */
function toDateTimeInputValue(isoString: string): string {
  const date = new Date(isoString)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** A `datetime-local` input's local-time value -> an ISO `Instant` string for the backend. */
function fromDateTimeInputValue(value: string): string {
  return new Date(value).toISOString()
}
