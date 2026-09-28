import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { LanguageSwitcher } from '../components/LanguageSwitcher'
import { api } from '../lib/api'
import { isAuthenticated, saveSession } from '../lib/auth'
import { useI18n } from '../lib/LanguageContext'

export function LoginPage() {
  const navigate = useNavigate()
  const { t } = useI18n()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (isAuthenticated()) {
    return <Navigate to="/projects" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const response = await api.login(email, password)
      saveSession(response.token, response.user)
      navigate('/projects')
    } catch (err) {
      setError(err instanceof Error ? err.message : t('login.failed'))
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
          <ul className="auth-features">
            <li>{t('login.feature.workspace')}</li>
            <li>{t('login.feature.pdf')}</li>
            <li>{t('login.feature.roles')}</li>
          </ul>
        </div>
      </aside>

      <main className="auth-panel">
        <form className="auth-card" onSubmit={handleSubmit}>
          <div className="auth-card-header">
            <div>
              <h2>{t('login.welcome')}</h2>
              <p>{t('login.subtitle')}</p>
            </div>
            <LanguageSwitcher />
          </div>

          <label className="field">
            <span>{t('login.email')}</span>
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
            <span>{t('login.password')}</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={t('login.passwordPlaceholder')}
              required
              autoComplete="current-password"
            />
          </label>

          {error && (
            <div className="alert alert-error" role="alert">
              {error}
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading ? t('login.submitting') : t('login.submit')}
          </button>

          <p className="auth-footnote">
            {t('login.demo')} <code>director@construction.local</code> / <code>Director123!</code>
          </p>
        </form>
      </main>
    </div>
  )
}
