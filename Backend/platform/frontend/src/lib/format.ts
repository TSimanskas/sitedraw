import type { DocumentVersionItem, DocumentVersionSource, ProjectStatus, UserRole } from '../types'
import { localeTag, translate, type Locale, type MessageKey } from './i18n'

const STATUS_KEYS: Record<ProjectStatus, MessageKey> = {
  DRAFT: 'status.draft',
  ACTIVE: 'status.active',
  COMPLETED: 'status.completed',
}

const ROLE_KEYS: Record<string, MessageKey> = {
  DIRECTOR: 'role.director',
  PROJECT_MANAGER: 'role.projectManager',
  SITE_WORKER: 'role.siteWorker',
}

const VERSION_SOURCE_KEYS: Record<DocumentVersionSource, MessageKey> = {
  ORIGINAL: 'version.original',
  UPLOAD: 'version.upload',
  MARKUP: 'version.markup',
}

const CATEGORY_KEYS: Record<string, MessageKey> = {
  Plans: 'category.Plans',
  Sections: 'category.Sections',
  Elevations: 'category.Elevations',
  Details: 'category.Details',
  Other: 'category.Other',
}

export function formatProjectStatus(status: ProjectStatus, locale: Locale = 'en'): string {
  return translate(locale, STATUS_KEYS[status] ?? 'status.active')
}

export function formatRole(role: UserRole | string, locale: Locale = 'en'): string {
  const key = ROLE_KEYS[role]
  if (key) {
    return translate(locale, key)
  }
  return role.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}

export function formatCategory(category: string | null | undefined, locale: Locale = 'en'): string {
  if (!category) {
    return '—'
  }
  const key = CATEGORY_KEYS[category]
  return key ? translate(locale, key) : category
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function getInitials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

export function formatDateTime(value: string, locale: Locale = 'en'): string {
  return new Intl.DateTimeFormat(localeTag(locale), {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function formatDocumentVersionSource(source?: DocumentVersionSource, locale: Locale = 'en'): string {
  if (!source) {
    return translate(locale, 'version.saved')
  }
  return translate(locale, VERSION_SOURCE_KEYS[source] ?? 'version.saved')
}

export function formatDocumentVersionAttribution(
  version: Pick<DocumentVersionItem, 'source' | 'uploadedByName'>,
  locale: Locale = 'en',
): string {
  const name = version.uploadedByName?.trim() || translate(locale, 'common.unknown')
  if (version.source === 'MARKUP') {
    return translate(locale, 'version.savedBy', { name })
  }
  return translate(locale, 'version.uploadedBy', { name })
}

export function formatDocumentVersionLabel(version: DocumentVersionItem, locale: Locale = 'en'): string {
  const name = version.uploadedByName?.trim() || translate(locale, 'common.unknown')
  const date = formatDateTime(version.uploadedAt, locale)
  if (version.source === 'ORIGINAL') {
    return translate(locale, 'version.labelOriginal', { number: version.versionNumber, name, date })
  }
  return translate(locale, 'version.label', {
    number: version.versionNumber,
    attribution: formatDocumentVersionAttribution(version, locale),
    date,
  })
}

export function formatAnnotationAuthor(name: string | null | undefined, locale: Locale = 'en'): string {
  return translate(locale, 'annotation.addedBy', { name: name?.trim() || translate(locale, 'common.unknown') })
}
