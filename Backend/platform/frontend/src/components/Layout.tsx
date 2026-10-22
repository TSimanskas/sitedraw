import { useState, type FormEvent } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { clearSession, getUser } from '../lib/auth'
import { formatRole, getInitials } from '../lib/format'
import { useI18n } from '../lib/LanguageContext'
import { LanguageSwitcher } from './LanguageSwitcher'
import { PasswordChangedDialog } from './PasswordChangedDialog'
import { PasswordRequirements } from './PasswordRequirements'
import { isStrongPassword } from '../lib/passwordPolicy'

export function Layout() {
  const navigate = useNavigate()
  const user = getUser()
  const { t, locale } = useI18n()
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordChanged, setPasswordChanged] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [savingPassword, setSavingPassword] = useState(false)

  function logout() {
    clearSession()
    navigate('/login')
  }

  function goToLoginAfterPasswordChange() {
    clearSession()
    navigate('/login')
  }

  async function handleChangePassword(event: FormEvent) {
    event.preventDefault()
    setPasswordError(null)
    if (!isStrongPassword(newPassword)) {
      setPasswordError(t('password.requirementsFailed'))
      return
    }
    setSavingPassword(true)
    try {
      await api.changePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setShowPasswordForm(false)
      setPasswordChanged(true)
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

          <div className="header-tools">
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
            <LanguageSwitcher variant="header" />
          </div>

          <div className="header-account">
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
              className="btn btn-ghost header-action"
              onClick={() => {
                setShowPasswordForm((open) => !open)
                setPasswordError(null)
              }}
            >
              {t('nav.password')}
            </button>
            <button type="button" className="btn btn-ghost header-action" onClick={logout}>
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
          <PasswordRequirements password={newPassword} />
          {passwordError && <div className="alert alert-error">{passwordError}</div>}
        </div>
      )}

      <PasswordChangedDialog
        open={passwordChanged}
        title={t('password.changedTitle')}
        body={t('password.changedBody')}
        actionLabel={t('password.goToLogin')}
        onGoToLogin={goToLoginAfterPasswordChange}
      />

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
