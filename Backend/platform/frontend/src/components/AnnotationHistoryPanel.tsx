import { formatAuditAction, formatRevisionAction } from '../lib/annotations'
import {
  formatAnnotationAuthor,
  formatDateTime,
  formatDocumentVersionLabel,
  formatDocumentVersionSource,
} from '../lib/format'
import { useI18n } from '../lib/LanguageContext'
import type { AnnotationItem, AnnotationRevision, AuditEventItem, DocumentVersionItem } from '../types'

interface AnnotationHistoryPanelProps {
  annotations: AnnotationItem[]
  selectedAnnotationId: string | null
  revisions: AnnotationRevision[]
  auditEvents: AuditEventItem[]
  versions: DocumentVersionItem[]
  currentVersion: number
  onSelectAnnotation: (annotationId: string) => void
  onOpenVersion: (versionNumber: number) => void
  onRollback: (annotationId: string) => void
  canEditSelected: boolean
  loading: boolean
}

export function AnnotationHistoryPanel({
  annotations,
  selectedAnnotationId,
  revisions,
  auditEvents,
  versions,
  currentVersion,
  onSelectAnnotation,
  onOpenVersion,
  onRollback,
  canEditSelected,
  loading,
}: AnnotationHistoryPanelProps) {
  const { t, locale } = useI18n()

  return (
    <aside className="viewer-sidebar">
      <section className="viewer-sidebar-section">
        <h2>{t('history.savedVersions')}</h2>
        {versions.length === 0 ? (
          <p className="viewer-sidebar-empty">{t('history.noVersions')}</p>
        ) : (
          <ul className="viewer-version-list">
            {versions.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className={`viewer-annotation-item${currentVersion === item.versionNumber ? ' is-active' : ''}`}
                  onClick={() => onOpenVersion(item.versionNumber)}
                >
                  <span className="viewer-annotation-type">{formatDocumentVersionLabel(item, locale)}</span>
                  <span className="viewer-annotation-meta">
                    {formatDocumentVersionSource(item.source, locale)}
                    {currentVersion === item.versionNumber ? ` · ${t('history.viewing')}` : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="viewer-sidebar-section">
        <h2>{t('history.annotations')}</h2>
        {annotations.length === 0 ? (
          <p className="viewer-sidebar-empty">{t('history.noMarkup')}</p>
        ) : (
          <ul className="viewer-annotation-list">
            {annotations.map((annotation) => (
              <li key={annotation.id}>
                <button
                  type="button"
                  className={`viewer-annotation-item${selectedAnnotationId === annotation.id ? ' is-active' : ''}`}
                  onClick={() => onSelectAnnotation(annotation.id)}
                >
                  <span className="viewer-annotation-type">{annotation.type}</span>
                  <span className="viewer-annotation-meta">
                    {formatAnnotationAuthor(annotation.createdByName, locale)}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="viewer-sidebar-section">
        <div className="viewer-sidebar-header">
          <h2>{t('history.changeHistory')}</h2>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={!selectedAnnotationId || !canEditSelected || loading}
            onClick={() => selectedAnnotationId && onRollback(selectedAnnotationId)}
          >
            {t('history.undoLast')}
          </button>
        </div>

        {selectedAnnotationId == null ? (
          <p className="viewer-sidebar-empty">{t('history.selectAnnotation')}</p>
        ) : revisions.length === 0 ? (
          <p className="viewer-sidebar-empty">{t('history.noRevisions')}</p>
        ) : (
          <ul className="viewer-revision-list">
            {revisions.map((revision) => (
              <li key={revision.id} className="viewer-revision-item">
                <strong>{formatRevisionAction(revision.action, locale)}</strong>
                <span>{revision.changedByName}</span>
                <time>{formatDateTime(revision.changedAt, locale)}</time>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="viewer-sidebar-section">
        <h2>{t('history.recentActivity')}</h2>
        {auditEvents.length === 0 ? (
          <p className="viewer-sidebar-empty">{t('history.noAudit')}</p>
        ) : (
          <ul className="viewer-audit-list">
            {auditEvents.slice(0, 8).map((event) => (
              <li key={event.id} className="viewer-audit-item">
                <strong>{formatAuditAction(event.action, locale)}</strong>
                <span>{event.userName ?? t('common.system')}</span>
                <time>{formatDateTime(event.createdAt, locale)}</time>
              </li>
            ))}
          </ul>
        )}
      </section>
    </aside>
  )
}
