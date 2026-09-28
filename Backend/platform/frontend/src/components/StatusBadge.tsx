import { formatProjectStatus } from '../lib/format'
import { useI18n } from '../lib/LanguageContext'
import type { ProjectStatus } from '../types'

export function StatusBadge({ status }: { status: ProjectStatus }) {
  const { locale } = useI18n()
  return <span className={`badge badge-${status.toLowerCase()}`}>{formatProjectStatus(status, locale)}</span>
}
