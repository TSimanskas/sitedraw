import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import { getUser } from '../lib/auth'
import { useI18n } from '../lib/LanguageContext'

export function ProjectsPage() {
  const user = getUser()
  const { t } = useI18n()
  const canCreate = user?.role !== 'SITE_WORKER'
  const [query, setQuery] = useState('')

  const { data: projects = [], isLoading, error } = useQuery({
    queryKey: ['projects', query],
    queryFn: () => api.listProjects(query),
  })

  return (
    <div className="page">
      <PageHeader
        eyebrow={t('projects.eyebrow')}
        title={t('projects.title')}
        description={t('projects.description')}
        actions={
          <div className="header-actions-row">
            <label className="search-field">
              <span className="sr-only">{t('projects.search')}</span>
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={t('projects.searchPlaceholder')}
              />
            </label>
            {canCreate ? (
              <Link to="/projects/new" className="btn btn-primary">
                {t('projects.new')}
              </Link>
            ) : null}
          </div>
        }
      />

      {isLoading && (
        <div className="loading-block">
          <span className="spinner" aria-hidden="true" />
          {t('projects.loading')}
        </div>
      )}

      {error && (
        <div className="alert alert-error">
          {error instanceof Error ? error.message : t('projects.loadFailed')}
        </div>
      )}

      {!isLoading && !error && projects.length === 0 && (
        <div className="panel">
          <EmptyState
            title={query ? t('projects.emptyNoMatch') : t('projects.emptyTitle')}
            description={query ? t('projects.emptyNoMatchDescription') : t('projects.emptyDescription')}
            action={
              !query && canCreate ? (
                <Link to="/projects/new" className="btn btn-primary">
                  {t('projects.createFirst')}
                </Link>
              ) : undefined
            }
          />
        </div>
      )}

      <div className="project-grid">
        {projects.map((project) => (
          <Link key={project.id} to={`/projects/${project.id}`} className="project-card">
            <div className="project-card-header">
              <div className="project-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
                  <path d="M3 21h18M5 21V7l7-4 7 4v14" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>
              <StatusBadge status={project.status} />
            </div>

            <h2>{project.name}</h2>

            <dl className="meta-list">
              <div>
                <dt>{t('projects.site')}</dt>
                <dd>{project.siteAddress ?? t('projects.noAddress')}</dd>
              </div>
              <div>
                <dt>{t('projects.client')}</dt>
                <dd>{project.clientName ?? '—'}</dd>
              </div>
            </dl>
          </Link>
        ))}
      </div>
    </div>
  )
}
