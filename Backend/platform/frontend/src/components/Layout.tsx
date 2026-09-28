import { useState, type FormEvent } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { clearSession, getUser } from '../lib/auth'
import { formatRole, getInitials } from '../lib/format'
import { useI18n } from '../lib/LanguageContext'
import { LanguageSwitcher } from './LanguageSwitcher'

export function Layout() {
  const navigate = useNavigate()
  const user = getUser()
  const { t, locale } = useI18n()
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [savingPassword, setSavingPassword] = useState(false)

  function logout() {
    clearSession()
    navigate('/login')
  }

  async function handleChangePassword(event: FormEvent) {
    event.preventDefault()
    setPasswordError(null)
    setPasswordMessage(null)
    setSavingPassword(true)
    try {
      await api.changePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setPasswordMessage(t('nav.passwordUpdated'))
      setShowPasswordForm(false)
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : t('nav.passwordFailed'))
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="header-inner">
          <div className="header-left">
            <Link to="/projects" className="brand">
              <span className="brand-mark" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M3 21h18M5 21V7l7-4 7 4v14" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M9 21v-6h6v6" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </span>
              <span className="brand-text">
                <strong>SiteDraw</strong>
                <span>{t('brand.subtitle')}</span>
              </span>
            </Link>

            <nav className="nav-links" aria-label={t('nav.main')}>
              <NavLink to="/projects" className={({ isActive }) => (isActive ? 'active' : undefined)}>
                {t('nav.projects')}
              </NavLink>
              {user?.role === 'DIRECTOR' && (
                <NavLink to="/users" className={({ isActive }) => (isActive ? 'active' : undefined)}>
                  {t('nav.team')}
                </NavLink>
              )}
            </nav>
          </div>

          <div className="header-right">
            <LanguageSwitcher variant="header" />
            {user && (
              <div className="user-chip">
                <span className="avatar" aria-hidden="true">
                  {getInitials(user.fullName)}
                </span>
                <span className="user-meta">
                  <strong>{user.fullName}</strong>
                  <span>{formatRole(user.role, locale)}</span>
                </span>
              </div>
            )}
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setShowPasswordForm((open) => !open)
                setPasswordError(null)
                setPasswordMessage(null)
              }}
            >
              {t('nav.password')}
            </button>
            <button type="button" className="btn btn-ghost" onClick={logout}>
              {t('nav.signOut')}
            </button>
          </div>
        </div>
      </header>

      {showPasswordForm && (
        <div className="password-banner">
          <form className="password-form" onSubmit={handleChangePassword}>
            <label className="field">
              <span>{t('nav.currentPassword')}</span>
              <input
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                required
              />
            </label>
            <label className="field">
              <span>{t('nav.newPassword')}</span>
              <input
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                minLength={8}
                required
              />
            </label>
            <button type="submit" className="btn btn-primary btn-sm" disabled={savingPassword}>
              {savingPassword ? t('nav.savingPassword') : t('nav.updatePassword')}
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowPasswordForm(false)}>
              {t('nav.cancel')}
            </button>
          </form>
          {passwordError && <div className="alert alert-error">{passwordError}</div>}
          {passwordMessage && <div className="alert alert-success">{passwordMessage}</div>}
        </div>
      )}

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
