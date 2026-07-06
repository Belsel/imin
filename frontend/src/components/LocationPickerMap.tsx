import L from 'leaflet'
import { useRef, useState } from 'react'
import { MapContainer, Marker, TileLayer, useMapEvents } from 'react-leaflet'

export interface LocationPickerMapProps {
  /**
   * Seed position for the pin and the initial map center.
   * null means no pin is placed; map opens at a default overview.
   */
  initialPosition: { lat: number; lng: number } | null
  /**
   * Called whenever the user places or moves the pin.
   * Clearing the location is the parent's responsibility (via its own state).
   */
  onChange: (position: { lat: number; lng: number }) => void
  /** Tailwind class(es) applied to the MapContainer div. Defaults to "h-48 w-full rounded-xl". */
  className?: string
}

const pickerIcon = L.divIcon({
  className: '',
  html: '<div style="width:22px;height:22px;border-radius:50%;background:#0B6E65;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.45)"></div>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
  popupAnchor: [0, -14],
})

/** Inner component — must be rendered inside MapContainer so useMapEvents works. */
function ClickHandler({ onPlace }: { onPlace: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onPlace(e.latlng.lat, e.latlng.lng)
    },
  })
  return null
}

/**
 * Interactive Leaflet map for picking a location.
 *
 * - Click anywhere to drop a teal pin.
 * - Drag the pin to refine position.
 * - Both actions call `onChange` with the new `{ lat, lng }`.
 * - Shows a hint overlay when no pin is placed yet.
 *
 * Semi-controlled: the parent owns the canonical lat/lng for form
 * submission; this component owns the marker's visual position internally.
 * `initialPosition` seeds the marker at mount only — subsequent position
 * changes are tracked locally and propagated via `onChange`.
 */
export default function LocationPickerMap({
  initialPosition,
  onChange,
  className,
}: LocationPickerMapProps) {
  const [pinPlaced, setPinPlaced] = useState(initialPosition !== null)
  const [currentPosition, setCurrentPosition] = useState<{ lat: number; lng: number } | null>(
    initialPosition,
  )
  const markerRef = useRef<L.Marker>(null)

  // MapContainer ignores center/zoom changes after mount — seed them once.
  const center: [number, number] = initialPosition
    ? [initialPosition.lat, initialPosition.lng]
    : [40.416775, -3.70379]
  const zoom = initialPosition ? 13 : 5

  function handlePlace(lat: number, lng: number) {
    const pos = { lat, lng }
    setPinPlaced(true)
    setCurrentPosition(pos)
    onChange(pos)
  }

  function handleDragEnd() {
    const m = markerRef.current
    if (!m) return
    const { lat, lng } = m.getLatLng()
    const pos = { lat, lng }
    setCurrentPosition(pos)
    onChange(pos)
  }

  return (
    <div className="relative">
      <MapContainer
        center={center}
        zoom={zoom}
        scrollWheelZoom={false}
        className={className ?? 'h-48 w-full rounded-xl'}
        style={{ overflow: 'hidden' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <ClickHandler onPlace={handlePlace} />
        {currentPosition && (
          <Marker
            position={[currentPosition.lat, currentPosition.lng]}
            icon={pickerIcon}
            draggable
            ref={markerRef}
            eventHandlers={{ dragend: handleDragEnd }}
          />
        )}
      </MapContainer>

      {/* Overlay — pointer-events-none so map clicks still register */}
      {!pinPlaced && (
        <div className="pointer-events-none absolute inset-0 flex items-end justify-center pb-3 z-[400]">
          <span className="rounded-full bg-surface/90 px-3 py-1.5 text-sm font-medium font-body text-text-muted shadow-sm">
            Click the map to place a pin
          </span>
        </div>
      )}
    </div>
  )
}
