// Thin fetch wrapper shared by every feature slice. `apiFetch` handles the
// base URL, JWT attachment, and error normalization; everything else in this
// file is a typed helper built on top of it. Later slices (groups, chat,
// activities, routing) should add their own typed wrapper functions here (or
// in sibling files importing `apiFetch`) rather than calling `fetch`
// directly from components.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

const TOKEN_STORAGE_KEY = 'imin.token'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setStoredToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_STORAGE_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
  }
}

/**
 * Error thrown for any non-2xx response. Carries the HTTP status and
 * whatever message the backend provided (Spring's default error body shape
 * is `{ message: string, ... }` for `ResponseStatusException`s; falls back
 * to the raw response text, then the status text, if no JSON body is
 * present).
 */
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Skip attaching the Authorization header even if a token is stored. */
  skipAuth?: boolean
}

/**
 * Generic fetch helper: resolves the path against `VITE_API_BASE_URL`,
 * JSON-encodes `body` when present, attaches `Authorization: Bearer <token>`
 * when a token is stored (unless `skipAuth` is set), and throws `ApiError`
 * for non-2xx responses. Returns `undefined` for empty (e.g. 204/empty-body)
 * responses.
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { body, skipAuth, headers, ...rest } = options

  const requestHeaders = new Headers(headers)
  if (body !== undefined && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json')
  }

  if (!skipAuth) {
    const token = getStoredToken()
    if (token) {
      requestHeaders.set('Authorization', `Bearer ${token}`)
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: requestHeaders,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  const text = await response.text()
  const data = text ? safeJsonParse(text) : undefined

  if (!response.ok) {
    const message = extractErrorMessage(data, text, response.statusText)
    throw new ApiError(response.status, message)
  }

  return data as T
}

function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function extractErrorMessage(data: unknown, rawText: string, statusText: string): string {
  if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
    return data.message
  }
  if (typeof data === 'string' && data.length > 0) {
    return data
  }
  return rawText || statusText || 'Request failed'
}

// ---------------------------------------------------------------------------
// Auth endpoints
// ---------------------------------------------------------------------------

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export interface RegisterResponse {
  email: string
  emailVerified: boolean
  message: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresIn: number
}

export interface ProfileResponse {
  id: number
  email: string
  displayName: string
  bio: string | null
  emailVerified: boolean
  createdAt: string
}

export function register(request: RegisterRequest): Promise<RegisterResponse> {
  return apiFetch<RegisterResponse>('/api/auth/register', {
    method: 'POST',
    body: request,
    skipAuth: true,
  })
}

export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: request,
    skipAuth: true,
  })
}

export function verifyEmail(token: string): Promise<void> {
  return apiFetch<void>(`/api/auth/verify-email?token=${encodeURIComponent(token)}`, {
    method: 'GET',
    skipAuth: true,
  })
}

export function getMyProfile(): Promise<ProfileResponse> {
  return apiFetch<ProfileResponse>('/api/users/me', { method: 'GET' })
}

export interface UpdateProfileRequest {
  displayName?: string
  bio?: string
}

/**
 * Partial update: omit a field to leave it unchanged. `bio: ''` clears the
 * bio; `displayName`, if provided, must be non-blank (the backend rejects a
 * blank value with a 400 `ApiError` — display name can never be cleared to
 * empty).
 */
export function updateProfile(request: UpdateProfileRequest): Promise<ProfileResponse> {
  return apiFetch<ProfileResponse>('/api/users/me', { method: 'PATCH', body: request })
}

/** Backend URL that kicks off the Google OAuth2 login flow (full page navigation, not a fetch). */
export function googleOAuthUrl(): string {
  return `${API_BASE_URL}/oauth2/authorization/google`
}

// ---------------------------------------------------------------------------
// Category endpoints
// ---------------------------------------------------------------------------

export interface CategoryResponse {
  id: number
  name: string
}

export interface UpdateCategoryPreferencesRequest {
  categoryIds: number[]
}

/** Fixed, developer-curated category taxonomy — read-only, no create/edit/delete API. */
export function listCategories(): Promise<CategoryResponse[]> {
  return apiFetch<CategoryResponse[]>('/api/categories', { method: 'GET' })
}

export function getMyCategoryPreferences(): Promise<CategoryResponse[]> {
  return apiFetch<CategoryResponse[]>('/api/users/me/category-preferences', { method: 'GET' })
}

/** Full-replace: submits the caller's complete set of category preferences (empty array clears all). */
export function updateMyCategoryPreferences(
  request: UpdateCategoryPreferencesRequest,
): Promise<CategoryResponse[]> {
  return apiFetch<CategoryResponse[]>('/api/users/me/category-preferences', {
    method: 'PUT',
    body: request,
  })
}

// ---------------------------------------------------------------------------
// Group endpoints
// ---------------------------------------------------------------------------

export interface CreateGroupRequest {
  name: string
  description: string | null
  latitude: number
  longitude: number
  categoryIds: number[]
}

export interface UpdateGroupRequest {
  name: string
  description: string | null
}

export interface GroupResponse {
  id: number
  name: string
  description: string | null
  latitude: number
  longitude: number
  createdAt: string
  memberCount: number
  isAdmin: boolean
  isMember: boolean
  categories: CategoryResponse[]
}

export interface GroupRecommendationResponse {
  id: number
  name: string
  description: string | null
  latitude: number
  longitude: number
  createdAt: string
  memberCount: number
  categories: CategoryResponse[]
  distanceKm: number
  matchingCategoryCount: number
}

export interface PublicGroupRecommendationResponse {
  id: number
  name: string
  description: string | null
  latitude: number
  longitude: number
  memberCount: number
  categories: CategoryResponse[]
}

export interface GroupMemberResponse {
  userId: number
  displayName: string
  isAdmin: boolean
  joinedAt: string
}

export interface GroupBanResponse {
  userId: number
  displayName: string
  bannedAt: string
  bannedById: number
}

export function createGroup(request: CreateGroupRequest): Promise<GroupResponse> {
  return apiFetch<GroupResponse>('/api/groups', { method: 'POST', body: request })
}

export function getGroup(id: number): Promise<GroupResponse> {
  return apiFetch<GroupResponse>(`/api/groups/${id}`, { method: 'GET' })
}

export function updateGroup(id: number, request: UpdateGroupRequest): Promise<GroupResponse> {
  return apiFetch<GroupResponse>(`/api/groups/${id}`, { method: 'PATCH', body: request })
}

export function deleteGroup(id: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${id}`, { method: 'DELETE' })
}

export function searchGroups(q?: string): Promise<GroupResponse[]> {
  const query = q ? `?q=${encodeURIComponent(q)}` : ''
  return apiFetch<GroupResponse[]>(`/api/groups/search${query}`, { method: 'GET' })
}

export function getMyGroups(): Promise<GroupResponse[]> {
  return apiFetch<GroupResponse[]>('/api/groups/mine', { method: 'GET' })
}

export function recommendGroups(
  latitude: number,
  longitude: number,
  limit?: number,
): Promise<GroupRecommendationResponse[]> {
  const params = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
  })
  if (limit) params.set('limit', String(limit))
  return apiFetch<GroupRecommendationResponse[]>(`/api/groups/recommendations?${params.toString()}`, {
    method: 'GET',
  })
}

/**
 * Unauthenticated recommended-groups feed for the public landing page (see
 * specs/public-group-recommendations/spec.md). Deliberately passes
 * skipAuth so no Authorization header is ever sent, even if a stale token
 * happens to be in localStorage from a prior session on this device --
 * the backend ignores any Authorization header on this path regardless
 * (Requirement 3), but skipping it client-side too keeps this call
 * trivially cache/CDN-safe and avoids sending a token the endpoint will
 * never look at.
 */
export function getPublicRecommendations(): Promise<PublicGroupRecommendationResponse[]> {
  return apiFetch<PublicGroupRecommendationResponse[]>('/api/groups/public-recommendations', {
    method: 'GET',
    skipAuth: true,
  })
}

export function addGroupCategory(groupId: number, categoryId: number): Promise<GroupResponse> {
  return apiFetch<GroupResponse>(`/api/groups/${groupId}/categories`, {
    method: 'POST',
    body: { categoryId },
  })
}

export function removeGroupCategory(groupId: number, categoryId: number): Promise<GroupResponse> {
  return apiFetch<GroupResponse>(`/api/groups/${groupId}/categories/${categoryId}`, {
    method: 'DELETE',
  })
}

export function listGroupMembers(groupId: number): Promise<GroupMemberResponse[]> {
  return apiFetch<GroupMemberResponse[]>(`/api/groups/${groupId}/members`, { method: 'GET' })
}

export function joinGroup(groupId: number): Promise<GroupResponse> {
  return apiFetch<GroupResponse>(`/api/groups/${groupId}/members`, { method: 'POST' })
}

export function leaveGroup(groupId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/members/me`, { method: 'DELETE' })
}

export function kickMember(groupId: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' })
}

export function listGroupBans(groupId: number): Promise<GroupBanResponse[]> {
  return apiFetch<GroupBanResponse[]>(`/api/groups/${groupId}/bans`, { method: 'GET' })
}

export function banMember(groupId: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/bans/${userId}`, { method: 'POST' })
}

export function unbanMember(groupId: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/bans/${userId}`, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------
// Friends / blocks endpoints
// ---------------------------------------------------------------------------

export interface FriendResponse {
  userId: number
  displayName: string
  addedAt: string
}

export interface BlockResponse {
  userId: number
  displayName: string
  blockedAt: string
}

export function listFriends(): Promise<FriendResponse[]> {
  return apiFetch<FriendResponse[]>('/api/friends', { method: 'GET' })
}

export function addFriend(userId: number): Promise<void> {
  return apiFetch<void>(`/api/friends/${userId}`, { method: 'POST' })
}

export function removeFriend(userId: number): Promise<void> {
  return apiFetch<void>(`/api/friends/${userId}`, { method: 'DELETE' })
}

export function listBlocks(): Promise<BlockResponse[]> {
  return apiFetch<BlockResponse[]>('/api/blocks', { method: 'GET' })
}

export function blockUser(userId: number): Promise<void> {
  return apiFetch<void>(`/api/blocks/${userId}`, { method: 'POST' })
}

export function unblockUser(userId: number): Promise<void> {
  return apiFetch<void>(`/api/blocks/${userId}`, { method: 'DELETE' })
}

// ---------------------------------------------------------------------------
// Group chat endpoints
// ---------------------------------------------------------------------------

export interface PostGroupChatMessageRequest {
  body: string
}

export interface GroupChatMessageResponse {
  id: number
  groupId: number
  senderId: number
  senderDisplayName: string | null
  body: string
  createdAt: string
}

/**
 * Poll a group's chat. `after` omitted returns the most recent page
 * (chronological order, ready to render top-to-bottom); `after` set to the
 * last-seen message id returns only newer messages (ascending id), for
 * incremental append.
 */
export function getGroupMessages(groupId: number, after?: number): Promise<GroupChatMessageResponse[]> {
  const query = after !== undefined ? `?after=${encodeURIComponent(String(after))}` : ''
  return apiFetch<GroupChatMessageResponse[]>(`/api/groups/${groupId}/messages${query}`, {
    method: 'GET',
  })
}

export function postGroupMessage(
  groupId: number,
  request: PostGroupChatMessageRequest,
): Promise<GroupChatMessageResponse> {
  return apiFetch<GroupChatMessageResponse>(`/api/groups/${groupId}/messages`, {
    method: 'POST',
    body: request,
  })
}

/** Message's own sender or any current group admin only; enforced server-side (403 otherwise). */
export function deleteGroupMessage(groupId: number, messageId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/messages/${messageId}`, {
    method: 'DELETE',
  })
}

// ---------------------------------------------------------------------------
// Direct chat (DM) endpoints
// ---------------------------------------------------------------------------

export interface PostDirectMessageRequest {
  body: string
}

export interface DirectMessageResponse {
  id: number
  threadId: number
  senderId: number
  senderDisplayName: string | null
  body: string
  createdAt: string
}

export interface DirectThreadResponse {
  threadId: number
  otherUserId: number | null
  otherUserDisplayName: string | null
  lastMessageBody: string | null
  lastMessageAt: string | null
  createdAt: string
}

/** The caller's DM threads, each with the other participant's info and a last-message preview. */
export function listDirectThreads(): Promise<DirectThreadResponse[]> {
  return apiFetch<DirectThreadResponse[]>('/api/dm/threads', { method: 'GET' })
}

/**
 * Poll the DM thread with `otherUserId`, keyed by that user's id (not a
 * thread id) since a thread is created implicitly on first message. `after`
 * omitted returns the most recent page; `after` set to the last-seen message
 * id returns only newer messages. A pair that has never exchanged a message
 * returns an empty list, not an error.
 */
export function getDirectMessages(otherUserId: number, after?: number): Promise<DirectMessageResponse[]> {
  const query = after !== undefined ? `?after=${encodeURIComponent(String(after))}` : ''
  return apiFetch<DirectMessageResponse[]>(`/api/dm/${otherUserId}/messages${query}`, {
    method: 'GET',
  })
}

/**
 * Send a direct message to `otherUserId`. No friend-add precondition in
 * either direction — any user can message any other user. Fails with a 403
 * `ApiError` if the recipient has blocked the caller.
 */
export function postDirectMessage(
  otherUserId: number,
  request: PostDirectMessageRequest,
): Promise<DirectMessageResponse> {
  return apiFetch<DirectMessageResponse>(`/api/dm/${otherUserId}/messages`, {
    method: 'POST',
    body: request,
  })
}

// ---------------------------------------------------------------------------
// Activity endpoints
// ---------------------------------------------------------------------------

export interface CreateActivityRequest {
  name: string
  description: string | null
  scheduledTime: string
  latitude: number | null
  longitude: number | null
}

export interface UpdateActivityRequest {
  name: string
  description: string | null
  scheduledTime: string
  latitude: number | null
  longitude: number | null
}

export interface ActivityResponse {
  id: number
  groupId: number
  ownerId: number
  ownerDisplayName: string | null
  name: string
  description: string | null
  scheduledTime: string
  latitude: number | null
  longitude: number | null
  createdAt: string
}

/** A group's activity calendar — members only. Backend already returns it sorted chronologically. */
export function listActivities(groupId: number): Promise<ActivityResponse[]> {
  return apiFetch<ActivityResponse[]>(`/api/groups/${groupId}/activities`, { method: 'GET' })
}

/** Any current member of the group may create an activity; the caller becomes its owner. */
export function createActivity(
  groupId: number,
  request: CreateActivityRequest,
): Promise<ActivityResponse> {
  return apiFetch<ActivityResponse>(`/api/groups/${groupId}/activities`, {
    method: 'POST',
    body: request,
  })
}

export function getActivity(groupId: number, activityId: number): Promise<ActivityResponse> {
  return apiFetch<ActivityResponse>(`/api/groups/${groupId}/activities/${activityId}`, {
    method: 'GET',
  })
}

/** Owner-or-group-admin only; enforced server-side (403 otherwise). */
export function updateActivity(
  groupId: number,
  activityId: number,
  request: UpdateActivityRequest,
): Promise<ActivityResponse> {
  return apiFetch<ActivityResponse>(`/api/groups/${groupId}/activities/${activityId}`, {
    method: 'PATCH',
    body: request,
  })
}

/** Owner-or-group-admin only; enforced server-side (403 otherwise). */
export function deleteActivity(groupId: number, activityId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/activities/${activityId}`, {
    method: 'DELETE',
  })
}

// ---------------------------------------------------------------------------
// Routing endpoints
// ---------------------------------------------------------------------------

export interface RouteStep {
  instruction: string
  distanceMeters: number
  durationSeconds: number
}

export interface RouteResponse {
  distanceMeters: number
  durationSeconds: number
  /** Route geometry as `[lat, lng]` pairs, already in Leaflet's expected order. */
  coordinates: [number, number][]
  steps: RouteStep[]
}

/**
 * Turn-by-turn directions between two points, proxied server-side (the
 * OpenRouteService API key never reaches the browser — see design.md §6a).
 * `profile` defaults to `driving-car` on the backend if omitted. Can fail
 * with a 502 `ApiError` if the upstream routing provider is unavailable —
 * callers should catch and show a clear message rather than crash.
 */
export function getDirections(
  startLat: number,
  startLng: number,
  endLat: number,
  endLng: number,
  profile?: string,
): Promise<RouteResponse> {
  const params = new URLSearchParams({
    startLat: String(startLat),
    startLng: String(startLng),
    endLat: String(endLat),
    endLng: String(endLng),
  })
  if (profile) params.set('profile', profile)
  return apiFetch<RouteResponse>(`/api/routing/directions?${params.toString()}`, {
    method: 'GET',
  })
}
