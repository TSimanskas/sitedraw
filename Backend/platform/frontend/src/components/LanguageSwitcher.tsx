import { useI18n } from '../lib/LanguageContext'
import type { Locale } from '../lib/i18n'

const OPTIONS: Array<{ locale: Locale; label: string }> = [
  { locale: 'lt', label: 'LT' },
  { locale: 'en', label: 'EN' },
]

export function LanguageSwitcher({ variant = 'light' }: { variant?: 'header' | 'light' | 'viewer' }) {
  const { locale, setLocale, t } = useI18n()

  return (
    <div className={`language-switcher language-switcher-${variant}`} role="group" aria-label={t('language.label')}>
      {OPTIONS.map((option) => (
        <button
          key={option.locale}
          type="button"
          className={locale === option.locale ? 'is-active' : undefined}
          aria-pressed={locale === option.locale}
          onClick={() => setLocale(option.locale)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
