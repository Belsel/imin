---
status: verified
owner:
created: 2026-07-02
---

# Group Map Discovery

## Problem

The Groups discovery page surfaces recommended nearby groups as a list only.
Users have no spatial context — they cannot tell at a glance where groups are
relative to themselves or to each other. The `MapView` component already exists
and `recommendGroups` already returns latitude/longitude per group, but nothing
connects them: the map is unused and `MapView` accepts no real data. Users
navigating the discovery page to find a local group must rely entirely on a
distance number in the list, with no visual orientation.

## Requirements

1. `MapView` must accept a prop describing an array of group pins — each pin
   carrying at minimum a group id, name, latitude, and longitude — instead of
   the current hardcoded New York placeholder. The component's public interface
   must no longer contain any hardcoded location data.

2. `MapView` must accept a prop for the user's own location (latitude and
   longitude). When provided, the map renders a visually distinct marker at
   that position to indicate "you are here". The user-location marker must be
   distinguishable from group markers (e.g. different icon or colour).

3. `MapView` must center on the user's provided location when one is supplied.
   When no user location is provided the map must not render (see requirement 5).

4. Each group pin must show a popup containing the group's name. The popup must
   include a navigable link to that group's detail page (`/groups/<id>`).

5. On `GroupsListPage`, the map panel renders above the "Recommended for you"
   list and below the search bar. It is shown only when geolocation succeeds
   and recommendations are available. When geolocation is denied, unavailable,
   or the recommendation fetch fails, the map is not rendered; the existing
   amber-tinted explanatory note for recommendation failure already shown by the
   page satisfies the degraded-state requirement — no additional map-specific
   error message is required.

6. The map and the "Recommended for you" list must be driven by the same
   `recommendations` state that is already fetched by the single
   `recommendGroups` call. No additional API request must be issued to populate
   the map.

7. The map must be responsive: it must fill the available container width on
   all screen sizes without horizontal overflow.

## Out of scope

- Routing directions from the groups map. Turn-by-turn routing is already
  handled separately on the ActivityDetailPage and must not be added here.
- Displaying search results on the map. Only recommendations are mapped.
- Creating a group by clicking on the map.
- Clustering markers when many groups are close together.
- Any change to how geolocation permission is requested or how the
  recommendation API call is made.

## Acceptance criteria

- [ ] A user who opens the Groups page and grants geolocation permission sees a
      Leaflet map rendered above the recommendations list, centered on their
      current location.
- [ ] Every group returned in the recommendations response has a marker on the
      map at its lat/lng coordinates.
- [ ] Clicking a group marker opens a popup showing the group name and a link;
      clicking that link navigates to `/groups/<id>` for the correct group.
- [ ] The user's own location is shown on the map with a marker that is visually
      distinct from group markers.
- [ ] A user who denies geolocation (or whose geolocation is unavailable) sees
      no map — only the existing amber explanatory note — and can still use the
      search bar. The page does not throw a runtime error.
- [ ] Network tooling (DevTools / test mocks) shows exactly one call to the
      recommendations endpoint on page load; the map and list render from the
      same response.
- [ ] The map fills its container width without horizontal scrollbar at viewport
      widths of 375 px, 768 px, and 1280 px.
- [ ] `MapView` no longer contains any hardcoded coordinates. Removing the New
      York default causes no test or type error.

## Design notes

(filled in by architect)

## Implementation notes

**MapView.tsx** (`frontend/src/components/MapView.tsx`): Complete rewrite. Exports `GroupPin` interface (`id`, `name`, `lat`, `lng`) and `MapViewProps` interface (`groupPins`, `userLocation?`, `centerLat?`, `centerLng?`). No hardcoded coordinates anywhere. Returns `null` when center cannot be determined (neither `centerLat`/`centerLng` nor `userLocation` provided). Custom `L.divIcon` markers: teal `#0B6E65` circle (14×14px) for group pins, amber `#D97706` circle (18×18px) for user location — visually distinct by size and color. Each group marker popup shows the group name and a `Link` to `/groups/${pin.id}`. `MapContainer` uses `className="h-56 w-full rounded-2xl"` — fills container width at all breakpoints without horizontal overflow.

**GroupsListPage.tsx** (`frontend/src/routes/GroupsListPage.tsx`): Added `const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null)`. After `const position = await getCurrentPosition()` succeeds (and before the `cancelled` guard for `recommendGroups`), added `setUserLocation({ lat: position.latitude, lng: position.longitude })`. Field names verified from `apiClient.ts`: `GroupRecommendationResponse` has `latitude: number` and `longitude: number`, so the mapping call uses `lat: g.latitude, lng: g.longitude`. MapView rendered between `</form>` and the search results section, gated on `!isLoadingRecommendations && !recommendationsError && recommendations && recommendations.length > 0 && userLocation`. No additional API call: both the map and the recommendations list consume the same `recommendations` state from the single `recommendGroups` call.

**Geolocation failure**: When `getCurrentPosition()` throws, `setUserLocation` is never called (it sits before the call to `recommendGroups` which also throws), so `userLocation` stays `null` and the MapView render condition fails — no map is shown, only the existing amber warning note. No runtime error.

## Verification

(filled in by tester)
