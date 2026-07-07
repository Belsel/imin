import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import GroupListItem from '../components/GroupListItem'
import MapView from '../components/MapView'
import NavBar from '../components/NavBar'
import { getCurrentPosition } from '../lib/geolocation'
import {
  ApiError,
  getMyGroups,
  recommendGroups,
  searchGroups,
} from '../lib/apiClient'
import type { GroupRecommendationResponse, GroupResponse } from '../lib/apiClient'

/**
 * Groups discovery page: recommended groups (distance + category overlap,
 * via the browser Geolocation API) plus a name search box. Geolocation
 * denial/unavailability degrades gracefully — recommendations are simply
 * skipped with an explanatory note, the rest of the page still works.
 */
export default function GroupsListPage() {
  const [recommendations, setRecommendations] = useState<GroupRecommendationResponse[] | null>(null)
  const [recommendationsError, setRecommendationsError] = useState<string | null>(null)
  const [isLoadingRecommendations, setIsLoadingRecommendations] = useState(true)
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null)

  const [query, setQuery] = useState('')
  const [searchResults, setSearchResults] = useState<GroupResponse[] | null>(null)
  const [isSearching, setIsSearching] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [hasSearched, setHasSearched] = useState(false)

  const [myGroups, setMyGroups] = useState<GroupResponse[] | null>(null)
  const [myGroupsError, setMyGroupsError] = useState<string | null>(null)
  const [isLoadingMyGroups, setIsLoadingMyGroups] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function loadRecommendations() {
      setIsLoadingRecommendations(true)
      setRecommendationsError(null)
      try {
        const position = await getCurrentPosition()
        if (cancelled) return
        setUserLocation({ lat: position.latitude, lng: position.longitude })
        const results = await recommendGroups(position.latitude, position.longitude)
        if (!cancelled) setRecommendations(results)
      } catch (err) {
        if (cancelled) return
        // Geolocation denial/unavailability and recommendation-fetch errors
        // both land here; either way we don't block the rest of the page —
        // just explain why recommendations aren't shown.
        setRecommendations(null)
        setRecommendationsError(
          err instanceof ApiError
            ? err.message
            : err instanceof Error
              ? err.message
              : 'Could not load recommendations.',
        )
      } finally {
        if (!cancelled) setIsLoadingRecommendations(false)
      }
    }

    loadRecommendations()
    return () => {
      cancelled = true
    }
  }, [])

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

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSearching(true)
    setSearchError(null)
    setHasSearched(true)
    try {
      const results = await searchGroups(query.trim() || undefined)
      setSearchResults(results)
    } catch (err) {
      setSearchError(err instanceof ApiError ? err.message : 'Search failed. Please try again.')
    } finally {
      setIsSearching(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-3xl p-6">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-3xl font-bold font-display text-text">Groups</h1>
          <Link
            to="/groups/new"
            className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Create a group
          </Link>
        </div>

        <form onSubmit={handleSearch} className="mb-8 flex gap-2">
          <input
            type="text"
            placeholder="Search groups by name"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
          <button
            type="submit"
            disabled={isSearching}
            className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
          >
            {isSearching ? 'Searching…' : 'Search'}
          </button>
        </form>

        <section className="mb-10">
          <h2 className="mb-3 text-2xl font-bold font-display text-text">Your Groups</h2>
          {isLoadingMyGroups && <p className="text-sm text-text-muted">Loading your groups…</p>}
          {!isLoadingMyGroups && myGroupsError && (
            <p className="rounded-lg bg-warning px-3 py-2 text-sm text-warning-text">{myGroupsError}</p>
          )}
          {!isLoadingMyGroups && !myGroupsError && myGroups && myGroups.length === 0 && (
            <p className="text-sm text-text-muted">You haven't joined any groups yet.</p>
          )}
          {!isLoadingMyGroups && !myGroupsError && myGroups && myGroups.length > 0 && (
            <ul className="flex flex-col gap-2">
              {myGroups.map((group) => (
                <GroupListItem key={group.id} group={group} />
              ))}
            </ul>
          )}
        </section>

        {!isLoadingRecommendations &&
          !recommendationsError &&
          recommendations &&
          recommendations.length > 0 &&
          userLocation && (
            <div className="mb-6">
              <MapView
                groupPins={recommendations.map((g) => ({
                  id: g.id,
                  name: g.name,
                  lat: g.latitude,
                  lng: g.longitude,
                }))}
                userLocation={userLocation}
              />
            </div>
          )}

        {hasSearched && (
          <section className="mb-10">
            <h2 className="mb-3 text-2xl font-bold font-display text-text">Search results</h2>
            {searchError && <p className="text-sm text-error">{searchError}</p>}
            {!searchError && searchResults && searchResults.length === 0 && (
              <p className="text-sm text-text-muted">No groups matched your search.</p>
            )}
            {!searchError && searchResults && searchResults.length > 0 && (
              <ul className="flex flex-col gap-2">
                {searchResults.map((group) => (
                  <GroupListItem key={group.id} group={group} />
                ))}
              </ul>
            )}
          </section>
        )}

        <section>
          <h2 className="mb-3 text-2xl font-bold font-display text-text">Recommended for you</h2>
          {isLoadingRecommendations && <p className="text-sm text-text-muted">Loading recommendations…</p>}
          {!isLoadingRecommendations && recommendationsError && (
            <p className="rounded-lg bg-warning px-3 py-2 text-sm text-warning-text">
              {recommendationsError} Showing search only — try searching by name above instead.
            </p>
          )}
          {!isLoadingRecommendations && !recommendationsError && recommendations && recommendations.length === 0 && (
            <p className="text-sm text-text-muted">No recommendations yet — try searching, or create a group.</p>
          )}
          {!isLoadingRecommendations && !recommendationsError && recommendations && recommendations.length > 0 && (
            <ul className="flex flex-col gap-2">
              {recommendations.map((group) => (
                <GroupListItem key={group.id} group={group} distanceKm={group.distanceKm} />
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
