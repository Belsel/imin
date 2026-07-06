import { useEffect, useState } from 'react'

export interface ReverseGeocodeResult {
  placeName: string | null
  loading: boolean
  error: boolean
}

interface NominatimReverseResponse {
  display_name?: string
  /** Nominatim returns `{ error: "Unable to geocode" }` for ocean/unmapped points. */
  error?: string
  address?: {
    name?: string
    road?: string
    suburb?: string
    neighbourhood?: string
    city?: string
    town?: string
    village?: string
    county?: string
    state?: string
    country?: string
  }
}

function buildPlaceName(data: NominatimReverseResponse): string {
  // display_name is always present on success; prefer it.
  if (data.display_name) {
    return data.display_name.length > 80
      ? data.display_name.slice(0, 77) + '…'
      : data.display_name
  }
  // Fallback composition from address parts.
  const addr = data.address ?? {}
  const parts = [
    addr.name ?? addr.road ?? addr.suburb ?? addr.neighbourhood,
    addr.city ?? addr.town ?? addr.village,
    addr.country,
  ].filter(Boolean)
  return parts.length > 0 ? parts.join(', ') : 'Unknown location'
}

/**
 * Reverse-geocodes a lat/lng pair via Nominatim.
 *
 * - Accepts null input (returns `{ placeName: null, loading: false, error: false }`).
 * - Debounces 300 ms before firing; cancels in-flight requests on change.
 * - Uses `lat`/`lng` as separate primitive deps to avoid spurious refetches
 *   when the parent creates a new object with identical values.
 * - User-Agent cannot be set by browsers (forbidden header); Nominatim's
 *   usage-policy alternative — the `email` query param — is used instead.
 */
export function useReverseGeocode(
  coords: { lat: number; lng: number } | null,
): ReverseGeocodeResult {
  const [result, setResult] = useState<ReverseGeocodeResult>({
    placeName: null,
    loading: false,
    error: false,
  })

  const lat = coords?.lat
  const lng = coords?.lng

  useEffect(() => {
    if (lat === undefined || lng === undefined) {
      setResult({ placeName: null, loading: false, error: false })
      return
    }

    const controller = new AbortController()
    setResult({ placeName: null, loading: true, error: false })

    const timer = setTimeout(async () => {
      try {
        const url =
          `https://nominatim.openstreetmap.org/reverse` +
          `?lat=${lat}&lon=${lng}&format=json&accept-language=en` +
          `&email=punsetrulestheworld%40gmail.com`
        const response = await fetch(url, { signal: controller.signal })
        if (!response.ok) throw new Error(`HTTP ${response.status}`)
        const data = (await response.json()) as NominatimReverseResponse
        if (data.error) throw new Error(data.error)
        setResult({ placeName: buildPlaceName(data), loading: false, error: false })
      } catch (err) {
        if ((err as Error).name === 'AbortError') return
        setResult({ placeName: null, loading: false, error: true })
      }
    }, 300)

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [lat, lng]) // primitive deps — avoids refetch on reference-equal object

  return result
}
