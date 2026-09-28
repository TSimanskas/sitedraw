import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { PageHeader } from '../components/PageHeader'
import { api } from '../lib/api'
import { useI18n } from '../lib/LanguageContext'

export function CreateProjectPage() {
  const navigate = useNavigate()
  const { t } = useI18n()
  const [name, setName] = useState('')
  const [siteAddress, setSiteAddress] = useState('')
  const [clientName, setClientName] = useState('')

  const mutation = useMutation({
    mutationFn: api.createProject,
    onSuccess: (project) => navigate(`/projects/${project.id}`),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate({
      name,
      siteAddress: siteAddress || undefined,
      clientName: clientName || undefined,
    })
  }

  return (
    <div className="page page-narrow">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/projects">{t('nav.projects')}</Link>
        <span aria-hidden="true">/</span>
        <span>{t('create.breadcrumb')}</span>
      </nav>

      <PageHeader title={t('create.title')} description={t('create.description')} />

      <div className="panel">
        <form className="stack-form" onSubmit={handleSubmit}>
          <label className="field">
            <span>{t('create.name')}</span>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('create.namePlaceholder')}
              required
            />
          </label>

          <label className="field">
            <span>{t('create.siteAddress')}</span>
            <input
              value={siteAddress}
              onChange={(e) => setSiteAddress(e.target.value)}
              placeholder={t('create.sitePlaceholder')}
            />
          </label>

          <label className="field">
            <span>{t('create.clientName')}</span>
            <input
              value={clientName}
              onChange={(e) => setClientName(e.target.value)}
              placeholder={t('create.clientPlaceholder')}
            />
          </label>

          {mutation.error && (
            <div className="alert alert-error">
              {mutation.error instanceof Error ? mutation.error.message : t('create.failed')}
            </div>
          )}

          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => navigate('/projects')}>
              {t('common.cancel')}
            </button>
            <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
              {mutation.isPending ? t('create.creating') : t('create.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
