import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import NavBar from '../components/NavBar'
import { getCurrentPosition } from '../lib/geolocation'
import { ApiError, createGroup, listCategories } from '../lib/apiClient'
import type { CategoryResponse } from '../lib/apiClient'

/**
 * Create-group form. Location is NOT a manual field — the creator's current
 * geolocation is captured at submission time and sent with the create
 * request (group location is derived from the creator's location at
 * creation and is immutable afterward, per spec.md Groups / design.md
 * §1.3). If geolocation truly isn't available at submit time, that is
 * surfaced as a clear, specific error rather than silently sending garbage
 * coordinates or failing silently.
 */
export default function CreateGroupPage() {
  const navigate = useNavigate()

  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [categoriesError, setCategoriesError] = useState<string | null>(null)
  const [isLoadingCategories, setIsLoadingCategories] = useState(true)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<number[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    listCategories()
      .then((result) => {
        if (!cancelled) setCategories(result)
      })
      .catch((err) => {
        if (!cancelled) {
          setCategoriesError(err instanceof ApiError ? err.message : 'Could not load categories.')
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoadingCategories(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  function toggleCategory(categoryId: number) {
    setSelectedCategoryIds((current) =>
      current.includes(categoryId)
        ? current.filter((id) => id !== categoryId)
        : [...current, categoryId],
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      let position
      try {
        position = await getCurrentPosition()
      } catch (geoError) {
        setError(
          `Could not determine your location, which is required to create a group: ${
            geoError instanceof Error ? geoError.message : 'unknown error'
          }. Please enable location access and try again.`,
        )
        return
      }

      const group = await createGroup({
        name,
        description: description.trim() ? description : null,
        latitude: position.latitude,
        longitude: position.longitude,
        categoryIds: selectedCategoryIds,
      })
      navigate(`/groups/${group.id}`, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the group. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="mx-auto max-w-xl p-6">
        <h1 className="mb-6 text-3xl font-bold font-display text-text">Create a group</h1>

        <form onSubmit={handleSubmit} className="rounded-2xl bg-surface p-6 shadow-sm border border-border">
          <label className="mb-4 block">
            <span className="mb-1 block text-sm font-medium font-body text-text-muted">Name</span>
            <input
              type="text"
              required
              maxLength={200}
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </label>

          <label className="mb-4 block">
            <span className="mb-1 block text-sm font-medium font-body text-text-muted">Description</span>
            <textarea
              maxLength={2000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={4}
              className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </label>

          <div className="mb-4">
            <span className="mb-1 block text-sm font-medium font-body text-text-muted">Categories</span>
            {isLoadingCategories && <p className="text-sm text-text-muted">Loading categories…</p>}
            {categoriesError && <p className="text-sm text-error">{categoriesError}</p>}
            {!isLoadingCategories && !categoriesError && (
              <div className="flex flex-wrap gap-2">
                {categories.map((category) => {
                  const selected = selectedCategoryIds.includes(category.id)
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
            )}
          </div>

          <p className="mb-4 text-sm text-text-muted font-body">
            Your group's location will be captured automatically from your current device location
            when you submit this form. It can't be changed afterward.
          </p>

          {error && <p className="mb-4 text-sm text-error">{error}</p>}

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50"
          >
            {isSubmitting ? 'Creating…' : 'Create group'}
          </button>
        </form>
      </div>
    </div>
  )
}
