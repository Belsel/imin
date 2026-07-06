import { Link } from 'react-router'
import { useAuth } from '../context/AuthContext'
import NavBar from '../components/NavBar'

/**
 * Authenticated home/dashboard route. Confirms auth works end to end and
 * links into the groups/friends/profile pages added in this pass.
 */
export default function HomePage() {
  const { user } = useAuth()

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <div className="flex flex-col items-center justify-center gap-6 p-6 mt-16">
        <div className="text-center">
          <p className="text-xs font-semibold font-body tracking-[0.2em] uppercase text-primary mb-2">WELCOME BACK</p>
          <h1 className="text-4xl font-extrabold font-display text-text">{user?.displayName}</h1>
          <p className="mt-2 text-sm text-text-muted font-body">{user?.email}</p>
        </div>
        <div className="flex flex-wrap gap-3 justify-center">
          <Link
            to="/groups"
            className="rounded-full bg-primary px-5 py-2.5 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Browse groups
          </Link>
          <Link
            to="/friends"
            className="rounded-full border border-border px-5 py-2.5 font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Friends &amp; blocks
          </Link>
          <Link
            to="/profile"
            className="rounded-full border border-border px-5 py-2.5 font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Profile
          </Link>
        </div>
      </div>
    </div>
  )
}
