# Activity Location Picker — Technical Design

## Chosen approach

Two new files are introduced; two existing route files are surgically modified. No
changes to `apiClient.ts`, `leafletIconFix.ts`, `MapView.tsx`, or
`RoutingControl.tsx`.

```
frontend/src/
  components/
    LocationPickerMap.tsx     NEW – controlled map-picker component
  hooks/
    useReverseGeocode.ts      NEW – Nominatim reverse-geocode hook
  routes/
    GroupDetailPage.tsx       MODIFIED – create form + activity list cards
    ActivityDetailPage.tsx    MODIFIED – edit form + detail view place name
```

---

## Spec flag: `User-Agent` header is a forbidden header in browsers

The spec (R5) requires every Nominatim request to include a `User-Agent` header
whose value identifies the app. In browsers, `User-Agent` is a **forbidden
header name** in the Fetch API specification — the browser silently ignores any
attempt to set it. This is not a tooling limitation; it is the spec.

**Resolution**: append the contact email as an `email` query parameter, which
Nominatim's usage policy explicitly accepts as an alternative identification
mechanism for browser-based clients. Every Nominatim URL in
`useReverseGeocode` ends with `&email=punsetrulestheworld%40gmail.com`. The
browser's own `User-Agent` (which includes the browser name and OS) still
flows through as normal. This satisfies Nominatim's "identify your
application" requirement in the browser context.

The "verifiable in browser DevTools Network tab" criterion is met — the
`email` param appears in the request URL.

---

## 1. `LocationPickerMap` component

**File:** `frontend/src/components/LocationPickerMap.tsx`

### Props interface

```typescript
export interface LocationPickerMapProps {
  /**
   * Seed position for the pin and the initial map center.
   * null means no pin is placed; map opens at world view (zoom 2).
   */
  initialPosition: { lat: number; lng: number } | null
  /**
   * Called whenever the user places or moves the pin.
   * Called with null when the parent clears the location
   * (clearing is the parent's responsibility via its own state).
   */
  onChange: (position: { lat: number; lng: number }) => void
  /** Tailwind class(es) applied to the MapContainer div. Defaults to "h-48 w-full rounded-xl". */
  className?: string
}
```

`onChange` only fires upward (place + drag-end); the parent's "Remove" button
updates parent state directly without going through this component.

### Icon

Use `L.divIcon` (same approach as `MapView.tsx`) — no dependency on
`leafletIconFix.ts`, no asset URL to break under Vite.

```typescript
const pickerIcon = L.divIcon({
  className: '',
  html: '<div style="width:22px;height:22px;border-radius:50%;background:#0B6E65;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.45)"></div>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
  popupAnchor: [0, -14],
})
```

A 22 px teal circle with a white border — slightly larger than `MapView`'s 14 px
group pin so it reads as "selected location" rather than a background pin.

Rejected alternatives:
- Teardrop/pin SVG shape: `iconAnchor` at the tip is tricky to get right,
  especially for dragging. The circle is unambiguous at any drag position.
- `L.Icon.Default` (with `leafletIconFix.ts`): importing it here would create a
  second import of the fix side-effect — harmless but unnecessary.

### Internal state

`LocationPickerMap` is a **controlled component**: it holds no lat/lng state.
`position` is derived from `initialPosition` at mount only (it seeds
`MapContainer`'s `center` and drives whether the `Marker` renders). All
subsequent position changes flow out via `onChange` to the parent; the parent
re-renders with a new `initialPosition` only when necessary (edit form initial
load; otherwise the parent is the single source of truth).

```typescript
const center: [number, number] = initialPosition
  ? [initialPosition.lat, initialPosition.lng]
  : [20, 0]                        // world view when no seed position
const zoom = initialPosition ? 13 : 2
```

`MapContainer` center/zoom are set at mount and do not track prop changes —
this is correct because:
- Create form: always mounts with `initialPosition=null` (fresh form).
- Edit form: mounts after the activity data is loaded, so `initialPosition` is
  already the real coordinates at mount time.

### Click-to-place

A child component inside `MapContainer` uses `useMapEvents` — the standard
react-leaflet hook for imperative map events:

```typescript
function ClickHandler({ onPlace }: { onPlace: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onPlace(e.latlng.lat, e.latlng.lng)
    },
  })
  return null
}
```

`ClickHandler` is rendered unconditionally — placing a new pin on an already-
pinned map overwrites the previous pin (the parent replaces its lat/lng state).

### Draggable marker

`react-leaflet`'s `<Marker>` accepts `draggable` and `eventHandlers`:

```typescript
const markerRef = useRef<L.Marker>(null)

<Marker
  position={[initialPosition.lat, initialPosition.lng]}
  icon={pickerIcon}
  draggable
  ref={markerRef}
  eventHandlers={{
    dragend() {
      const m = markerRef.current
      if (m) {
        const ll = m.getLatLng()
        onChange({ lat: ll.lat, lng: ll.lng })
      }
    },
  }}
/>
```

`dragend` fires once when the user releases the pin — not on every pixel — so
`onChange` is called at most once per drag gesture. This is the natural debounce
for geocoding: the hook fires on `dragend`, not during the drag.

### No-location overlay

When `initialPosition` is null (no pin placed yet), an absolutely positioned
overlay gives the user instruction without blocking map interaction:

```tsx
<div className="relative">
  <MapContainer ... className={className ?? 'h-48 w-full rounded-xl'} style={{ overflow: 'hidden' }}>
    <TileLayer ... />
    <ClickHandler onPlace={...} />
    {/* Marker only rendered when a position exists */}
  </MapContainer>

  {/* Overlay — pointer-events-none so map clicks still register */}
  {!hasPinPlaced && (
    <div className="pointer-events-none absolute inset-0 flex items-end justify-center pb-3 z-[400]">
      <span className="rounded-full bg-surface/90 px-3 py-1.5 text-sm font-medium font-body text-text-muted shadow-sm">
        Click the map to place a pin
      </span>
    </div>
  )}
</div>
```

`z-[400]` is above Leaflet tile layers (z-index ~200–300) but below any Leaflet
popups (z-index ~700). `pointer-events-none` on the wrapper ensures the overlay
does not intercept the `MapContainer` click handler.

`hasPinPlaced` is a local boolean derived from whether a `Marker` is currently
rendered. The parent passes `initialPosition`; when a pin is placed,
`onChange` fires and the parent state updates. The parent does not pass the
new position back into `initialPosition` (that prop is only for seeding at
mount). To know whether a pin has been placed, the component tracks a local
`pinPlaced` boolean that starts as `initialPosition !== null` and flips to
`true` on the first `ClickHandler` callback:

```typescript
const [pinPlaced, setPinPlaced] = useState(initialPosition !== null)
const [currentPosition, setCurrentPosition] = useState(initialPosition)

function handlePlace(lat: number, lng: number) {
  setPinPlaced(true)
  setCurrentPosition({ lat, lng })
  onChange({ lat, lng })
}

function handleDragEnd() {
  const m = markerRef.current
  if (m) {
    const ll = m.getLatLng()
    setCurrentPosition({ lat: ll.lat, lng: ll.lng })
    onChange({ lat: ll.lat, lng: ll.lng })
  }
}
```

This local `currentPosition` state is needed to keep the `Marker` position
correct between `onChange` calls (the parent does not echo position back). The
component is semi-controlled: the parent owns the canonical lat/lng for form
submission; the component owns the marker's visual position.

### Full JSX skeleton

```tsx
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

  const center: [number, number] = initialPosition ? [initialPosition.lat, initialPosition.lng] : [20, 0]
  const zoom = initialPosition ? 13 : 2

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
```

---

## 2. `useReverseGeocode` hook

**File:** `frontend/src/hooks/useReverseGeocode.ts`

### Signature

```typescript
export interface ReverseGeocodeResult {
  placeName: string | null
  loading: boolean
  error: boolean
}

export function useReverseGeocode(
  coords: { lat: number; lng: number } | null,
): ReverseGeocodeResult
```

### Nominatim URL

```
https://nominatim.openstreetmap.org/reverse
  ?lat=<lat>
  &lon=<lng>
  &format=json
  &accept-language=en
  &email=punsetrulestheworld%40gmail.com
```

- `accept-language=en` keeps display names in English regardless of server locale.
- `email` parameter satisfies Nominatim's identification requirement in browser
  context (see flag at top of document).

### Response type (internal)

```typescript
interface NominatimReverseResponse {
  display_name?: string
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
```

### Place name extraction

```typescript
function buildPlaceName(data: NominatimReverseResponse): string {
  // display_name is always present on success; prefer it.
  if (data.display_name) {
    return data.display_name.length > 80
      ? data.display_name.slice(0, 77) + '…'
      : data.display_name
  }
  // Fallback composition
  const addr = data.address ?? {}
  const parts = [
    addr.name ?? addr.road ?? addr.suburb ?? addr.neighbourhood,
    addr.city ?? addr.town ?? addr.village,
    addr.country,
  ].filter(Boolean)
  return parts.length > 0 ? parts.join(', ') : 'Unknown location'
}
```

`display_name` from Nominatim is a full comma-separated address string
(e.g. `"Parque del Retiro, Retiro, Madrid, Community of Madrid, 28009, Spain"`).
The 80-char cap keeps it readable in small chips without truncating meaning for
most addresses.

### Debounce and abort

The hook accepts raw coordinates. Since `LocationPickerMap`'s `onChange` only
fires on click and `dragend` (not per pixel), the natural rate is already low.
The 300 ms debounce is a defensive measure against rapid parent re-renders (e.g.
the activity list mounting all cards simultaneously) and to guard against a
future caller with a higher-frequency input.

```typescript
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
  }, [lat, lng])   // primitive deps — avoids refetch on reference-equal object

  return result
}
```

Using `lat` and `lng` as separate primitive deps rather than `coords` means the
effect does not re-fire when the parent creates a new `{ lat, lng }` object with
identical values (common in React render cycles).

### Rate-limit caveat (activity list)

When the activity list renders N cards with locations, N `useReverseGeocode`
instances each fire a fetch after 300 ms — effectively N concurrent requests.
Nominatim's policy is 1 req/sec; beyond ~3–4 concurrent requests the API
responds with 429. The spec explicitly marks rate-limit handling beyond graceful
error display as out of scope. Cards that receive a 429 will display the
`error: true` fallback text ("Location (place name unavailable)"). No further
action is required by the implementer.

---

## 3. Integration into `GroupDetailPage.tsx` (create form)

### State changes

**Remove** from `GroupDetailPage`:
- `isCapturingActivityLocation` state and setter
- `activityLocationError` state and setter
- `handleUseCurrentLocationForActivity` function
- The `getCurrentPosition` import from `../lib/geolocation` (if no longer used
  elsewhere in the file — check before removing)

**Keep unchanged:**
- `newActivityLocation: { latitude: number; longitude: number } | null` state

**Add:**
- `useReverseGeocode` call in the component body (at the top of the render,
  alongside other hook calls):

```typescript
const newPickerCoords = newActivityLocation
  ? { lat: newActivityLocation.latitude, lng: newActivityLocation.longitude }
  : null
const {
  placeName: newPlaceName,
  loading: newGeocoding,
  error: newGeocodeError,
} = useReverseGeocode(newPickerCoords)
```

### Reset on form close

When the user closes the create form (cancel button), reset `newActivityLocation`
to `null` so the map mounts fresh next time:

```typescript
onClick={() => {
  setShowCreateActivity(false)
  setNewActivityLocation(null)
  // also reset other create-form fields as appropriate
}}
```

### Replacement JSX for the "Location (optional)" section

Replace lines 653–684 of `GroupDetailPage.tsx` with:

```tsx
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
```

The "Remove" button clears `newActivityLocation` to null. The note about
location being optional is shown when no pin is placed, hidden once a pin is
placed (replaced by the place name chip).

`handleCreateActivity` needs no change — it already reads
`newActivityLocation?.latitude ?? null` and `newActivityLocation?.longitude ?? null`.

### Activity list cards (R8)

Extract activity rendering to an `ActivityCard` sub-component so
`useReverseGeocode` can be called at the top level of a component (hooks cannot
be called inside `.map()`):

```typescript
function ActivityCard({ activity }: { activity: ActivityResponse }) {
  const coords =
    activity.latitude !== null && activity.longitude !== null
      ? { lat: activity.latitude, lng: activity.longitude }
      : null
  const { placeName, loading, error } = useReverseGeocode(coords)

  return (
    <li>
      <Link
        to={`/groups/${/* groupId */}/activities/${activity.id}`}
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
```

`ActivityCard` needs the `groupId` (already in scope as `id` in
`GroupDetailPage`) — pass it as a prop or capture via closure. Closure is
simplest since it's defined inside `GroupDetailPage`.

Replace the `activities.map(...)` call with:

```tsx
{activities.map((activity) => (
  <ActivityCard key={activity.id} activity={activity} />
))}
```

The sub-component must be defined **outside** of `GroupDetailPage`'s render
function body (as a module-level function or after the component) so React
doesn't recreate it on every render. Declare it at module scope with `id` passed
as prop, or use `useCallback`/`useMemo` — module-scope is simplest.

Since `id` is in scope via closure only if defined inside the module after the
component (or the `groupId` passed as a prop), define `ActivityCard` at module
level taking `groupId` as a prop:

```typescript
function ActivityCard({
  groupId,
  activity,
}: {
  groupId: number
  activity: ActivityResponse
}) { ... }
```

And call it as `<ActivityCard key={activity.id} groupId={id} activity={activity} />`.

---

## 4. Integration into `ActivityDetailPage.tsx`

### State changes

**Remove** from `ActivityDetailPage`:
- `isCapturingEditLocation` state and setter
- `editLocationError` state and setter
- `handleUseCurrentLocationForEdit` function
- The `getCurrentPosition` import from `../lib/geolocation` (check if used
  elsewhere)

**Keep unchanged:**
- `editLocation: { latitude: number; longitude: number } | null`

**Add two `useReverseGeocode` calls:**

```typescript
// For the view-mode location section (R7)
const viewCoords =
  activity?.latitude !== null && activity?.longitude !== null
    ? { lat: activity.latitude as number, lng: activity.longitude as number }
    : null
const {
  placeName: viewPlaceName,
  loading: viewGeocoding,
  error: viewGeocodeError,
} = useReverseGeocode(viewCoords)

// For the edit-form location section (R3)
const editPickerCoords = editLocation
  ? { lat: editLocation.latitude, lng: editLocation.longitude }
  : null
const {
  placeName: editPlaceName,
  loading: editGeocoding,
  error: editGeocodeError,
} = useReverseGeocode(editPickerCoords)
```

Both calls are at the top of the component (before the early return guards).
`useReverseGeocode` safely handles `null` input, so calling it before the
`activity` is loaded is fine (it returns `{ placeName: null, loading: false,
error: false }`).

### Edit form: replace location section

Replace lines 302–327 of `ActivityDetailPage.tsx` with:

```tsx
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
```

`handleSaveEdit` needs no change — it already reads `editLocation?.latitude ?? null`.

### Detail view: add place name (R7)

In the `{hasLocation && ...}` block (starting around line 350), add the place
name paragraph between the section header row and the `RoutingControl`:

```tsx
{hasLocation && (
  <div className="rounded-2xl bg-surface p-6 shadow-sm border border-border">
    <div className="mb-3 flex items-center justify-between">
      <h2 className="text-2xl font-bold font-display text-text">Location</h2>
      <button type="button" onClick={handleGetDirections} ...>
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
    <RoutingControl ... />
    {route && ( ... )}   {/* existing directions steps — unchanged */}
  </div>
)}
```

---

## 5. Leaflet icon fix

`LocationPickerMap` uses `L.divIcon` exclusively and does not import
`leafletIconFix.ts`. `L.divIcon` embeds its visual in inline HTML — no asset
URL is involved, so Vite's module resolution cannot break it.

`RoutingControl.tsx` already imports `leafletIconFix.ts` for its default
`L.Icon.Default` markers. That file continues to serve its existing purpose.
`MapView.tsx` also uses `L.divIcon` and has never needed the fix.

No changes to `leafletIconFix.ts`.

---

## 6. Styling

All styling uses the existing design tokens from `frontend/src/index.css`:

| Element | Classes | Rationale |
|---|---|---|
| Map container (picker) | `h-48 w-full rounded-xl` | Compact enough for inline form; `rounded-xl` matches other form elements |
| Instruction overlay | `rounded-full bg-surface/90 px-3 py-1.5 text-sm font-medium font-body text-text-muted shadow-sm` | Ghosted pill sits on the map without fighting the tile layer visually |
| Place name (resolved) | `rounded-full bg-primary/10 px-3 py-1 text-sm font-medium font-body text-primary` | Teal-on-teal/10 chip — consistent with "info callout" convention; high enough contrast (teal text on near-white teal bg) |
| Place name (loading) | `text-sm font-body text-text-muted italic` | De-emphasised; italic signals in-progress state without needing a spinner |
| Place name (error) | `text-sm font-body text-text-muted` | Same muted weight as the loading state — error is informative, not alarming |
| Remove pin button | `rounded-full border border-border px-2 py-1 text-xs font-medium text-text-muted` | Same ghost-pill pattern used on existing "Remove location" button in both forms |

Typography uses `font-body` (DM Sans) for all supporting UI and `font-display`
(Syne) for section headings — unchanged from existing patterns.

---

## 7. Coordinate visibility audit

Every place where raw lat/lng numbers currently appear in the rendered DOM:

| Location | Current (defect) | Replacement |
|---|---|---|
| Create form (`GroupDetailPage` ~line 658) | `lat.toFixed(5), lng.toFixed(5)` | Place name chip or loading/error text |
| Edit form (`ActivityDetailPage` ~line 307) | `lat.toFixed(5), lng.toFixed(5)` | Place name chip or loading/error text |
| Activity list (`GroupDetailPage` ~line 724) | `"Has a location"` text | `useReverseGeocode` place name via `ActivityCard` |
| Activity detail view | No coordinates rendered (only `RoutingControl` map markers with `activity.name` as popup label) | Add place name text above the map |
| `RoutingControl` marker popup | `targetLabel ?? 'Target location'` — already uses `activity.name` | No change needed |

No `title` attributes, `aria-label` strings, or tooltip content render
coordinates anywhere in the existing code.

---

## 8. What the implementer must build vs. what already exists

**Must build:**
1. `frontend/src/components/LocationPickerMap.tsx` — full implementation per
   section 1 above.
2. `frontend/src/hooks/useReverseGeocode.ts` — full implementation per
   section 2 above.
3. Modify `GroupDetailPage.tsx`: remove GPS-button state/handlers, add
   `useReverseGeocode` call, replace location section JSX, add `ActivityCard`
   sub-component.
4. Modify `ActivityDetailPage.tsx`: remove GPS-button state/handlers, add two
   `useReverseGeocode` calls, replace edit-form location section JSX, add place
   name paragraph to the view-mode location section.

**Already exists and must not be changed:**
- `frontend/src/lib/apiClient.ts` — `CreateActivityRequest`, `UpdateActivityRequest`,
  `ActivityResponse` already carry `latitude: number | null` / `longitude: number | null`.
- `frontend/src/lib/leafletIconFix.ts` — continues to serve `RoutingControl` only.
- `frontend/src/components/MapView.tsx` — unchanged.
- `frontend/src/components/RoutingControl.tsx` — unchanged (directions feature untouched).
- All backend files — unchanged (R9).

---

## Trade-offs and rejected alternatives

**Semi-controlled `LocationPickerMap`** (local `currentPosition` state):
The alternative is a fully controlled component where the parent passes the
current position back as a prop on every change. That requires the parent to
update its state and the component to re-seed `MapContainer`'s view on each
change — but `MapContainer` ignores `center` prop changes after mount.
The semi-controlled design (local state for the marker, `onChange` to parent for
form submission values) is the correct Leaflet+react-leaflet pattern.

**No in-memory geocode cache:**
The spec marks caching as optional. Adding a `Map<string, string>` keyed on
`"${lat},${lng}"` would be cheap but adds implementation surface with no
required benefit. Rejected.

**Debounce inside `LocationPickerMap` before calling `onChange`:**
Could add a debounce inside the component to prevent `onChange` firing on every
intermediate drag position. Rejected: `dragend` already fires only once per
gesture, so there is nothing to debounce at the component level. The 300 ms
debounce inside `useReverseGeocode` handles any remaining edge cases.

**`ActivityCard` as a named export vs. module-level sub-component:**
A named export would make `ActivityCard` importable for tests. The implementer
may choose to export it. The design places it at module scope in
`GroupDetailPage.tsx` since it is tightly coupled to that page's concerns and
the spec does not mention standalone testing of cards.
