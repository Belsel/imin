import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../context/AuthContext'

/**
 * Gate for authenticated routes: shows nothing while the initial session
 * check is in flight, redirects to /login if there's no authenticated user,
 * otherwise renders the nested route.
 */
export default function ProtectedRoute() {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-text-muted">Loading…</p>
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
