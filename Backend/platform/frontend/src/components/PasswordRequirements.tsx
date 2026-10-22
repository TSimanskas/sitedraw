import { passwordRuleStatus, type PasswordRule } from '../lib/passwordPolicy'
import { useI18n } from '../lib/LanguageContext'
import type { MessageKey } from '../lib/i18n'

const RULES: { id: PasswordRule; label: MessageKey }[] = [
  { id: 'length', label: 'password.rule.length' },
  { id: 'upper', label: 'password.rule.upper' },
  { id: 'number', label: 'password.rule.number' },
  { id: 'symbol', label: 'password.rule.symbol' },
]

export function PasswordRequirements({ password }: { password: string }) {
  const { t } = useI18n()
  const status = passwordRuleStatus(password)

  return (
    <ul className="password-rules" aria-label={t('password.rulesLabel')}>
      {RULES.map((rule) => {
        const met = status[rule.id]
        return (
          <li key={rule.id} className={met ? 'met' : undefined}>
            <span className="sr-only">{met ? t('password.ruleMet') : t('password.ruleMissing')}</span>
            {t(rule.label)}
          </li>
        )
      })}
    </ul>
  )
}
