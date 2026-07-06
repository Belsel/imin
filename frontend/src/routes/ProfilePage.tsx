import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import NavBar from '../components/NavBar'
import { useAuth } from '../context/AuthContext'
import {
  ApiError,
  getMyCategoryPreferences,
  listCategories,
  updateMyCategoryPreferences,
  updateProfile,
} from '../lib/apiClient'
import type { CategoryResponse } from '../lib/apiClient'

/**
 * Profile page: an edit form for display name + bio (per spec.md, display
 * name can be changed at any time with no uniqueness constraint, and bio
 * can be set/edited/cleared at any time) plus the category-preferences
 * toggle UI from an earlier pass.
 */
export default function ProfilePage() {
  const { user, setUser } = useAuth()

  const [displayName, setDisplayName] = useState('')
  const [bio, setBio] = useState('')
  const [profileError, setProfileError] = useState<string | null>(null)
  const [isSavingProfile, setIsSavingProfile] = useState(false)
  const [profileSavedMessage, setProfileSavedMessage] = useState<string | null>(null)

  useEffect(() => {
    setDisplayName(user?.displayName ?? '')
    setBio(user?.bio ?? '')
  }, [user])

  async function handleProfileSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setProfileError(null)
    setProfileSavedMessage(null)
    setIsSavingProfile(true)
    try {
      const updated = await updateProfile({ displayName, bio })
      setUser(updated)
      setProfileSavedMessage('Profile saved.')
    } catch (err) {
      setProfileError(err instanceof ApiError ? err.message : 'Could not save profile.')
    } finally {
      setIsSavingProfile(false)
    }
  }

  const [allCategories, setAllCategories] = useState<CategoryResponse[]>([])
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [savedMessage, setSavedMessage] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setIsLoading(true)
      setLoadError(null)
      try {
        const [categories, preferences] = await Promise.all([
          listCategories(),
          getMyCategoryPreferences(),
        ])
        if (cancelled) return
        setAllCategories(categories)
        setSelectedIds(preferences.map((category) => category.id))
      } catch (err) {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : 'Could not load category preferences.')
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

  function toggleCategory(categoryId: number) {
    setSavedMessage(null)
    setSelectedIds((current) =>
      current.includes(categoryId)
        ? current.filter((id) => id !== categoryId)
        : [...current, categoryId],
    )
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaveError(null)
    setSavedMessage(null)
    setIsSaving(true)
    try {
      const updated = await updateMyCategoryPreferences({ categoryIds: selectedIds })
      setSelectedIds(updated.map((category) => category.id))
      setSavedMessage('Preferences saved.')
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'Could not save preferences.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-xl p-6">
        <h1 className="mb-6 text-3xl font-bold font-display text-text">Profile</h1>

        <div className="mb-6 rounded-2xl bg-surface p-6 shadow-sm border border-border">
          <p className="mb-4 text-sm text-text-muted font-body">{user?.email}</p>

          <form onSubmit={handleProfileSave}>
            <label className="mb-4 block">
              <span className="mb-1 block text-sm font-medium font-body text-text-muted">Display name</span>
              <input
                type="text"
                required
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </label>

            <label className="mb-4 block">
              <span className="mb-1 block text-sm font-medium font-body text-text-muted">Bio</span>
              <textarea
                maxLength={2000}
                value={bio}
                onChange={(event) => setBio(event.target.value)}
                rows={4}
                placeholder="No bio set yet."
                className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </label>

            {profileError && <p className="mb-4 text-sm text-error">{profileError}</p>}
            {profileSavedMessage && <p className="mb-4 text-sm text-success">{profileSavedMessage}</p>}

            <button
              type="submit"
              disabled={isSavingProfile}
              className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
            >
              {isSavingProfile ? 'Saving…' : 'Save profile'}
            </button>
          </form>
        </div>

        <div className="rounded-2xl bg-surface p-6 shadow-sm border border-border mb-6">
          <h2 className="mb-3 text-2xl font-bold font-display text-text">Category preferences</h2>
          <p className="mb-4 text-sm text-text-muted font-body">
            Used to drive group recommendations. Choose any categories you're interested in.
          </p>

          {isLoading && <p className="text-sm text-text-muted">Loading…</p>}
          {loadError && <p className="text-sm text-error">{loadError}</p>}

          {!isLoading && !loadError && (
            <form onSubmit={handleSave}>
              <div className="mb-4 flex flex-wrap gap-2">
                {allCategories.map((category) => {
                  const selected = selectedIds.includes(category.id)
                  return (
                    <button
                      key={category.id}
                      type="button"
                      onClick={() => toggleCategory(category.id)}
                      className={`rounded-full px-3 py-1 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 ${
                        selected
                          ? 'bg-primary text-on-primary'
                          : 'border border-border bg-background text-text-muted motion-safe:hover:bg-primary/10 motion-safe:hover:text-primary'
                      }`}
                    >
                      {category.name}
                    </button>
                  )
                })}
              </div>

              {saveError && <p className="mb-4 text-sm text-error">{saveError}</p>}
              {savedMessage && <p className="mb-4 text-sm text-success">{savedMessage}</p>}

              <button
                type="submit"
                disabled={isSaving}
                className="rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
              >
                {isSaving ? 'Saving…' : 'Save preferences'}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
