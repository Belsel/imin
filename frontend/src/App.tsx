import { Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from './context/AuthContext'
import LoginPage from './routes/LoginPage'
import RegisterPage from './routes/RegisterPage'
import VerifyEmailPage from './routes/VerifyEmailPage'
import OAuthCallbackPage from './routes/OAuthCallbackPage'
import HomePage from './routes/HomePage'
import LandingPage from './routes/LandingPage'
import ProtectedRoute from './routes/ProtectedRoute'
import GroupsListPage from './routes/GroupsListPage'
import CreateGroupPage from './routes/CreateGroupPage'
import GroupDetailPage from './routes/GroupDetailPage'
import ActivityDetailPage from './routes/ActivityDetailPage'
import ProfilePage from './routes/ProfilePage'
import FriendsPage from './routes/FriendsPage'
import DirectMessagesListPage from './routes/DirectMessagesListPage'
import DirectThreadPage from './routes/DirectThreadPage'

/**
 * Root route: unauthenticated visitors see the public LandingPage,
 * authenticated users see the existing HomePage dashboard. Mirrors
 * ProtectedRoute's isLoading handling so the initial session check doesn't
 * flash the wrong content.
 */
function RootRoute() {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-text-muted">Loading…</p>
      </div>
    )
  }

  return user ? <HomePage /> : <LandingPage />
}

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<RootRoute />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/oauth2/callback" element={<OAuthCallbackPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/groups" element={<GroupsListPage />} />
          <Route path="/groups/new" element={<CreateGroupPage />} />
          <Route path="/groups/:groupId" element={<GroupDetailPage />} />
          <Route path="/groups/:groupId/activities/:activityId" element={<ActivityDetailPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/friends" element={<FriendsPage />} />
          <Route path="/messages" element={<DirectMessagesListPage />} />
          <Route path="/messages/:userId" element={<DirectThreadPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App
