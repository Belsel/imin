// Shared browser geolocation helper. Used by both the groups discovery page
// (for distance-based recommendations) and the create-group form (to capture
// the group's immutable creation-time location) — see spec.md Groups and
// design.md §1.3, which both call for a single geolocation-acquisition
// mechanism rather than duplicating the browser API call per call site.

export interface Coordinates {
  latitude: number
  longitude: number
}

/**
 * Resolves the browser's current position as a simple `{latitude,
 * longitude}` pair, wrapping the callback-based `navigator.geolocation` API
 * in a promise. Rejects with a plain `Error` (human-readable message) if
 * geolocation is unsupported, permission is denied, the position is
 * unavailable, or the request times out — callers are expected to catch this
 * and degrade gracefully rather than crash (see spec.md/design.md: a denied
 * or unavailable geolocation must not block the page).
 */
export function getCurrentPosition(): Promise<Coordinates> {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('Geolocation is not supported by this browser.'))
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        })
      },
      (error) => {
        reject(new Error(geolocationErrorMessage(error)))
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 },
    )
  })
}

function geolocationErrorMessage(error: GeolocationPositionError): string {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return 'Location permission was denied.'
    case error.POSITION_UNAVAILABLE:
      return 'Your location is currently unavailable.'
    case error.TIMEOUT:
      return 'Timed out while trying to determine your location.'
    default:
      return 'Could not determine your location.'
  }
}
