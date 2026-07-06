import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext'

export default function NavBar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <nav className="flex flex-col border-b-2 border-primary bg-surface">
      <div className="flex items-center justify-between px-6 py-3">
        <div className="flex items-center gap-6">
          <Link
            to="/"
            className="text-xl font-extrabold font-display tracking-tight focus:outline-none focus:ring-2 focus:ring-primary focus:rounded"
          >
            <span className="text-text">Im</span><span className="text-primary">In</span>
          </Link>
          <div className="hidden md:flex items-center gap-6">
            <Link to="/groups" className="text-sm font-medium font-body text-text-muted transition-colors motion-safe:hover:text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:rounded">
              Groups
            </Link>
            <Link to="/friends" className="text-sm font-medium font-body text-text-muted transition-colors motion-safe:hover:text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:rounded">
              Friends
            </Link>
            <Link to="/messages" className="text-sm font-medium font-body text-text-muted transition-colors motion-safe:hover:text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:rounded">
              Messages
            </Link>
            <Link to="/profile" className="text-sm font-medium font-body text-text-muted transition-colors motion-safe:hover:text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:rounded">
              Profile
            </Link>
          </div>
        </div>
        <div className="flex items-center gap-4">
          {user && <span className="hidden md:block text-sm text-text-muted font-body">{user.displayName}</span>}
          <button
            type="button"
            onClick={handleLogout}
            className="hidden md:block rounded-full border border-border px-3 py-1.5 text-sm font-medium font-body text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
          >
            Log out
          </button>
          {/* Hamburger toggle — visible on mobile only */}
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            className="md:hidden rounded p-1 text-text-muted focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          >
            <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden="true">
              {menuOpen ? (
                <>
                  <line x1="4" y1="4" x2="18" y2="18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                  <line x1="18" y1="4" x2="4" y2="18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                </>
              ) : (
                <>
                  <line x1="3" y1="6" x2="19" y2="6" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                  <line x1="3" y1="11" x2="19" y2="11" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                  <line x1="3" y1="16" x2="19" y2="16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                </>
              )}
            </svg>
          </button>
        </div>
      </div>
      {/* Mobile menu panel */}
      {menuOpen && (
        <div className="md:hidden border-t border-border bg-surface px-6 py-4 flex flex-col gap-3">
          <Link to="/groups" onClick={() => setMenuOpen(false)} className="text-sm font-medium font-body text-text-muted motion-safe:hover:text-primary py-1">Groups</Link>
          <Link to="/friends" onClick={() => setMenuOpen(false)} className="text-sm font-medium font-body text-text-muted motion-safe:hover:text-primary py-1">Friends</Link>
          <Link to="/messages" onClick={() => setMenuOpen(false)} className="text-sm font-medium font-body text-text-muted motion-safe:hover:text-primary py-1">Messages</Link>
          <Link to="/profile" onClick={() => setMenuOpen(false)} className="text-sm font-medium font-body text-text-muted motion-safe:hover:text-primary py-1">Profile</Link>
          {user && <span className="text-sm text-text-muted font-body pt-2 border-t border-border">{user.displayName}</span>}
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-full border border-border px-3 py-1.5 text-sm font-medium font-body text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 w-fit"
          >
            Log out
          </button>
        </div>
      )}
    </nav>
  )
}
