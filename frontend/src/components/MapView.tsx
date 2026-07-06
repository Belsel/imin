import L from 'leaflet'
import { Link } from 'react-router'
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet'

export interface GroupPin {
  id: number
  name: string
  lat: number
  lng: number
}

export interface MapViewProps {
  groupPins: GroupPin[]
  userLocation?: { lat: number; lng: number }
  centerLat?: number
  centerLng?: number
}

const groupIcon = L.divIcon({
  className: '',
  html: '<div style="width:14px;height:14px;border-radius:50%;background:#0B6E65;border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,0.35)"></div>',
  iconSize: [14, 14],
  iconAnchor: [7, 7],
  popupAnchor: [0, -10],
})

const userIcon = L.divIcon({
  className: '',
  html: '<div style="width:18px;height:18px;border-radius:50%;background:#D97706;border:3px solid white;box-shadow:0 1px 6px rgba(0,0,0,0.4)"></div>',
  iconSize: [18, 18],
  iconAnchor: [9, 9],
  popupAnchor: [0, -12],
})

export default function MapView({ groupPins, userLocation, centerLat, centerLng }: MapViewProps) {
  const center: [number, number] | null =
    centerLat !== undefined && centerLng !== undefined
      ? [centerLat, centerLng]
      : userLocation
        ? [userLocation.lat, userLocation.lng]
        : null

  if (!center) return null

  return (
    <MapContainer
      center={center}
      zoom={13}
      scrollWheelZoom={false}
      className="h-56 w-full rounded-2xl"
      style={{ overflow: 'hidden' }}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {groupPins.map((pin) => (
        <Marker key={pin.id} position={[pin.lat, pin.lng]} icon={groupIcon}>
          <Popup>
            <p className="font-medium text-sm">{pin.name}</p>
            <Link
              to={`/groups/${pin.id}`}
              className="text-xs text-primary hover:underline"
            >
              View group →
            </Link>
          </Popup>
        </Marker>
      ))}
      {userLocation && (
        <Marker position={[userLocation.lat, userLocation.lng]} icon={userIcon}>
          <Popup>
            <p className="text-sm font-medium">Your location</p>
          </Popup>
        </Marker>
      )}
    </MapContainer>
  )
}
