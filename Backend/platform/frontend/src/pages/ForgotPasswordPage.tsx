import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { PasswordChangedDialog } from '../components/PasswordChangedDialog'
import { PasswordRequirements } from '../components/PasswordRequirements'
import { isStrongPassword } from '../lib/passwordPolicy'
import { LanguageSwitcher } from '../components/LanguageSwitcher'
import { api, ApiError } from '../lib/api'
import { isAuthenticated } from '../lib/auth'
import { useI18n } from '../lib/LanguageContext'
import type { MessageKey } from '../lib/i18n'

export function ForgotPasswordPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [errorKey, setErrorKey] = useState<MessageKey | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  if (isAuthenticated()) {
    return <Navigate to="/projects" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErrorKey(null)
    setSuccess(false)

    if (newPassword !== confirmPassword) {
      setErrorKey('forgot.mismatch')
      return
    }
    if (!isStrongPassword(newPassword)) {
      setErrorKey('password.requirementsFailed')
      return
    }

    setLoading(true)
    try {
      await api.forgotPassword(email.trim(), newPassword)
      setSuccess(true)
      setNewPassword('')
      setConfirmPassword('')
    } catch (error) {
      setErrorKey(mapForgotError(error))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-layout">
      <aside className="auth-brand">
        <div className="auth-brand-content">
          <div className="brand-mark brand-mark-lg" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 21h18M5 21V7l7-4 7 4v14" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M9 21v-6h6v6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <h1>SiteDraw</h1>
          <p className="auth-tagline">{t('login.tagline')}</p>
        </div>
      </aside>

      <main className="auth-panel">
        <form className="auth-card" onSubmit={handleSubmit}>
          <div className="auth-card-header">
            <div>
              <h2>{t('forgot.title')}</h2>
              <p>{t('forgot.subtitle')}</p>
            </div>
            <LanguageSwitcher />
          </div>

          <label className="field">
            <span>{t('forgot.email')}</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder={t('login.emailPlaceholder')}
              required
              autoComplete="username"
            />
          </label>

          <label className="field">
            <span>{t('forgot.newPassword')}</span>
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>
          <PasswordRequirements password={newPassword} />

          <label className="field">
            <span>{t('forgot.confirmPassword')}</span>
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>

          <div className="auth-card-actions">
            {errorKey && (
              <div className="alert alert-error" role="alert">
                {t(errorKey)}
              </div>
            )}

            <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
              {loading ? t('forgot.submitting') : t('forgot.submit')}
            </button>
            <Link to="/login" className="auth-forgot">
              {t('forgot.back')}
            </Link>
          </div>
        </form>
        <PasswordChangedDialog
          open={success}
          title={t('password.changedTitle')}
          body={t('password.changedBody')}
          actionLabel={t('password.goToLogin')}
          onGoToLogin={() => navigate('/login')}
        />
      </main>
    </div>
  )
}

function mapForgotError(error: unknown): MessageKey {
  const message = error instanceof ApiError ? error.message : ''
  if (message.includes('No account uses this email')) {
    return 'forgot.unknownEmail'
  }
  if (message.includes('inactive')) {
    return 'forgot.inactive'
  }
  return 'forgot.failed'
}
