---
status: in-progress
owner:
created: 2026-07-03
---

# Activity Location Picker

## Problem

The activity creation and edit forms currently let users attach a location only
by tapping "Use my current location", which captures the device's GPS position.
There is no way to pick an arbitrary point on a map. Once a location is
captured, the form displays the raw lat/lng coordinates (e.g.
`40.41667, -3.70379`) to the user — an internal representation that is
meaningless to most people and should never appear in the UI. The same
coordinate leak exists in the edit form's location display. The activity card
in the group calendar shows "Has a location" with no indication of where, and
the activity detail page shows a routing map with no human-readable place name.

## Requirements

### R1 — Map-based location picker (create form)

In `GroupDetailPage.tsx`, the "Location (optional)" section of the create
activity form must be replaced with an interactive Leaflet map. The user clicks
anywhere on the map to drop a pin at that point; that pin's lat/lng is used as
the activity location.

The picker must use the existing Leaflet + react-leaflet setup already present
in the project. A new reusable component (`LocationPickerMap` or similar) must
encapsulate the map, click-to-place, and draggable-pin behaviour so it can be
used in both the create and edit forms.

### R2 — Draggable pin

After a pin has been placed (whether by clicking the map or by loading an
existing location into the edit form), the pin must be draggable. Dragging it
to a new position updates the selected lat/lng and triggers a new reverse
geocode lookup for the new position.

### R3 — Map-based location picker (edit form)

In `ActivityDetailPage.tsx`, the "Location (optional)" section of the inline
edit form must be replaced with the same `LocationPickerMap` component. When
an activity already has a location, the map must open with the pin pre-placed
at the existing lat/lng.

### R4 — Location is optional and dismissible

Location remains optional on both the create and edit forms. The map picker
must include a clear affordance to:
- Skip adding a location at all (before placing a pin).
- Remove a previously placed pin (after placement), clearing the location.

When no location is selected, `latitude` and `longitude` are sent to the
backend as `null`, consistent with the existing `CreateActivityRequest` and
`UpdateActivityRequest` types.

### R5 — Reverse geocoding via Nominatim

After a pin is placed or dragged, the frontend must call the Nominatim reverse
geocoding API:

```
GET https://nominatim.openstreetmap.org/reverse?lat=<lat>&lon=<lng>&format=json
```

No backend proxy is needed; this is a direct browser fetch. Every request must
include a `User-Agent` header whose value identifies the app by name (e.g.
`ImIn-App`) to comply with Nominatim's usage policy.

The resolved place name is derived from the Nominatim response. The display
name must be human-readable (e.g. "Parque del Retiro, Madrid, Spain"). A
shortened form (e.g. `display_name` trimmed to a suitable length, or a
combination of `name`, `city`, and `country` from the `address` object) is
acceptable as long as it is identifiable without coordinates.

While the geocode request is in flight the UI must show a non-blocking loading
state (e.g. "Resolving location…") in place of the place name.

If the Nominatim call fails or returns no result, the UI must show a
human-readable fallback such as "Location selected (place name unavailable)"
— not raw coordinates and not a silent empty state.

### R6 — No coordinates visible to the user

Raw lat/lng numbers must never appear anywhere in the UI for any user, in any
state. This applies to:

- The create form after a pin is placed (currently renders `lat.toFixed(5),
  lng.toFixed(5)`).
- The edit form after a location is loaded or changed (same current defect).
- The activity list cards in `GroupDetailPage` (currently shows "Has a
  location" — must be replaced with the resolved place name).
- The activity detail page's location section heading and/or subtitle (must
  show the resolved place name, not coordinates).
- Tooltips, `title` attributes, `aria-label` values, and any other rendered
  content.

The lat/lng values returned by the backend in `ActivityResponse` are used only
internally (for placing map markers and calling the routing API); they must not
be rendered into the DOM as visible text.

### R7 — Place name on the activity detail page

`ActivityDetailPage.tsx` already renders a "Location" section when
`activity.latitude !== null`. That section must display the resolved place name
(obtained by reverse geocoding `activity.latitude`/`activity.longitude` on page
load) as visible text above or alongside the routing map. The "Get directions"
button and `RoutingControl` map remain unchanged.

### R8 — Place name on the activity list cards

In `GroupDetailPage.tsx`, each activity card that has a location must display
the resolved place name instead of the current "Has a location" placeholder.
Reverse geocoding for the list is performed per-card on the frontend, fired
concurrently for all activities that have coordinates when the list is rendered.

### R9 — Backend unchanged

No backend changes are required. `POST /api/groups/{groupId}/activities` and
`PATCH /api/groups/{groupId}/activities/{activityId}` already accept
`latitude: number | null` and `longitude: number | null`. `ActivityResponse`
already returns both fields. The `Activity` entity already stores them as
nullable `Double`. This spec makes no changes to any backend file.

## Out of scope

- Adding geocoding or place-name search ("search by address") — the picker is
  map-click only.
- Caching Nominatim responses across sessions (in-memory deduplication within
  a single page load is acceptable but not required).
- Showing a place name for the *group's* location on any page — groups use a
  separate picker and are out of scope here.
- Any change to how the routing/directions feature works (`RoutingControl`,
  `getDirections`, the routing backend).
- Displaying a map on the activity detail page in view mode where one is not
  already shown — the existing `RoutingControl` map satisfies the map
  requirement; this spec only adds the place name text alongside it.
- Rate-limit handling for Nominatim beyond showing a graceful error.
- Server-side reverse geocoding or caching.

## Acceptance criteria

- [ ] On the activity create form, a Leaflet map is rendered in the "Location
      (optional)" section. Clicking anywhere on the map places a pin at that
      point. The "Use my current location" button is removed.

- [ ] After a pin is placed on the create-form map, the resolved place name
      (from Nominatim) is displayed in the form. No lat/lng text appears
      anywhere on the form.

- [ ] The placed pin is draggable. Dragging it to a new position updates the
      displayed place name to reflect the new position via a fresh Nominatim
      call.

- [ ] The create form has a clear affordance to remove the pin / clear the
      location. Activating it hides the map picker (or empties the selection)
      and causes `latitude: null, longitude: null` to be submitted.

- [ ] Submitting the create form with a pin placed sends `latitude` and
      `longitude` as numbers to `POST /api/groups/{groupId}/activities`.
      Submitting without a pin sends both as `null`.

- [ ] On the activity edit form (inline in `ActivityDetailPage`), a Leaflet map
      is rendered in the "Location (optional)" section. When the activity
      already has a location the pin is pre-placed at those coordinates on
      mount. No raw coordinates are displayed.

- [ ] The edit-form map pin is draggable and triggers a new reverse geocode on
      drag end.

- [ ] The edit form has a clear affordance to remove the location. Activating
      it clears the selection; saving then sends `latitude: null, longitude:
      null`.

- [ ] On `ActivityDetailPage` in view mode, when an activity has a location,
      the resolved place name is displayed as visible text in the Location
      section. The `RoutingControl` map and "Get directions" button remain
      present and functional.

- [ ] On the activity list in `GroupDetailPage`, each activity card that has a
      location shows the resolved place name. The text "Has a location" no
      longer appears.

- [ ] While a Nominatim response is pending, the UI shows a non-blocking
      loading indicator (e.g. "Resolving location…") rather than coordinates
      or blank space.

- [ ] If Nominatim returns an error or no result, the UI shows a fallback
      message (e.g. "Location selected (place name unavailable)") rather than
      coordinates or blank space.

- [ ] No raw lat/lng numbers (e.g. `40.41667` or `-3.70379`) appear anywhere
      in the rendered DOM — not in visible text, not in `title` attributes, not
      in `aria-label` strings — in any of: the create form, the edit form, the
      activity list, or the activity detail page.

- [ ] Every Nominatim fetch includes a `User-Agent` header containing the app
      name (verifiable in browser DevTools Network tab).

- [ ] The `LocationPickerMap` component is used in both the create and edit
      forms (no duplicated map-picker logic).

## Design notes

(filled in by architect)

## Implementation notes

(filled in by implementer)

## Verification

(filled in by tester)
