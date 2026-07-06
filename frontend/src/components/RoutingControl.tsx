import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import { useEffect } from 'react'
import '../lib/leafletIconFix'

/**
 * Map + turn-by-turn routing display for a single target location (spec.md
 * Maps/Routing: "anywhere the application displays a map with a specific
 * target location... must offer real turn-by-turn routing/navigation
 * guidance to that target location, not merely a static pin").
 *
 * Implementation choice (see design.md §3/§6a, and the task's explicit
 * steer): `leaflet-routing-machine` is a vanilla-Leaflet plugin with its own
 * DOM-manipulating UI widget, not natively react-leaflet-aware — wiring it in
 * would mean fighting a non-React control inside a React tree for no
 * required benefit (the spec requires turn-by-turn guidance, not any
 * specific widget). Instead, this component renders an ordinary
 * `react-leaflet` map (`MapContainer`/`TileLayer`/`Marker`, the same
 * primitives `MapView.tsx` already uses) plus, when a `route` is supplied,
 * a `Polyline` for the route geometry — both fully declarative React
 * components, no imperative Leaflet-plugin lifecycle to manage. The
 * turn-by-turn instruction list itself is rendered as plain React UI by the
 * caller (`ActivityDetailPage`), using the backend's already-clean
 * `RouteResponse.steps` shape — this component only needs to draw the
 * geometry on the map.
 *
 * Does not call the routing API itself — the caller is responsible for
 * fetching the route (via `getDirections`) and passing the result in, so
 * this component stays a pure, reusable map-rendering piece independent of
 * how/when a route is requested.
 */
export interface RoutingControlProps {
  /** The target location to show a marker for (e.g. an activity's location). */
  target: { lat: number; lng: number }
  /** Optional label shown in the target marker's popup. */
  targetLabel?: string
  /** The device's current location, if known — shown as a second marker. */
  origin?: { lat: number; lng: number } | null
  /** Route geometry to draw, if a route has been requested and returned. */
  route?: { coordinates: [number, number][] } | null
  className?: string
}

export default function RoutingControl({
  target,
  targetLabel,
  origin,
  route,
  className,
}: RoutingControlProps) {
  const center: [number, number] = origin ? [origin.lat, origin.lng] : [target.lat, target.lng]

  return (
    <MapContainer
      center={center}
      zoom={13}
      scrollWheelZoom={false}
      className={className ?? 'h-96 w-full rounded-lg shadow'}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <Marker position={[target.lat, target.lng]}>
        <Popup>{targetLabel ?? 'Target location'}</Popup>
      </Marker>
      {origin && (
        <Marker position={[origin.lat, origin.lng]}>
          <Popup>Your location</Popup>
        </Marker>
      )}
      {route && route.coordinates.length > 0 && (
        <Polyline positions={route.coordinates} pathOptions={{ color: '#0B6E65', weight: 5 }} />
      )}
      <FitBoundsToMarkers target={target} origin={origin} route={route} />
    </MapContainer>
  )
}

/**
 * Adjusts the map's view to fit whatever's being shown — just the target
 * marker by default, or the target+origin+route once a route is loaded —
 * since the default `center`/`zoom` alone won't necessarily keep both ends
 * of a freshly-loaded route in view.
 */
function FitBoundsToMarkers({
  target,
  origin,
  route,
}: {
  target: { lat: number; lng: number }
  origin?: { lat: number; lng: number } | null
  route?: { coordinates: [number, number][] } | null
}) {
  const map = useMap()

  useEffect(() => {
    const points: [number, number][] = [[target.lat, target.lng]]
    if (origin) points.push([origin.lat, origin.lng])
    if (route) points.push(...route.coordinates)

    if (points.length === 1) {
      map.setView(points[0], 13)
    } else {
      map.fitBounds(points, { padding: [32, 32] })
    }
    // Only re-run when the actual point set changes, not on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, target.lat, target.lng, origin?.lat, origin?.lng, route])

  return null
}
