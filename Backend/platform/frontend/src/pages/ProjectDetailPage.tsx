import { useRef, useState, type FormEvent, Fragment } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { EmptyState } from '../components/EmptyState'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import {
  formatBytes,
  formatCategory,
  formatDocumentVersionLabel,
  formatDocumentVersionSource,
  formatProjectStatus,
  formatRole,
  getInitials,
} from '../lib/format'
import { getUser } from '../lib/auth'
import { useI18n } from '../lib/LanguageContext'
import type { ProjectMember, ProjectStatus, User } from '../types'

export function ProjectDetailPage() {
  const { projectId = '' } = useParams()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const user = getUser()
  const { t, locale } = useI18n()
  const canUpload = user?.role !== 'SITE_WORKER'

  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('Plans')
  const [selectedFileName, setSelectedFileName] = useState<string | null>(null)
  const [selectedUserId, setSelectedUserId] = useState('')
  const [documentQuery, setDocumentQuery] = useState('')
  const versionInputRef = useRef<HTMLInputElement>(null)
  const [versionTargetId, setVersionTargetId] = useState<string | null>(null)
  const [historyDocumentId, setHistoryDocumentId] = useState<string | null>(null)

  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState('')
  const [editSiteAddress, setEditSiteAddress] = useState('')
  const [editClientName, setEditClientName] = useState('')
  const [editStatus, setEditStatus] = useState<ProjectStatus>('DRAFT')

  const projectQuery = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => api.getProject(projectId),
    enabled: Boolean(projectId),
  })

  const documentsQuery = useQuery({
    queryKey: ['documents', projectId, documentQuery],
    queryFn: () => api.listDocuments(projectId, documentQuery),
    enabled: Boolean(projectId),
  })

  const project = projectQuery.data
  const documents = documentsQuery.data ?? []
  const members = project?.members ?? []
  const documentVersionsQuery = useQuery({
    queryKey: ['document-versions', historyDocumentId],
    queryFn: () => api.listDocumentVersions(historyDocumentId!),
    enabled: Boolean(historyDocumentId),
  })

  const canManageTeam =
    user?.role === 'DIRECTOR' ||
    (user?.role === 'PROJECT_MANAGER' && members.some((m) => m.userId === user.id))

  const canEditProject =
    user?.role === 'DIRECTOR' ||
    (user?.role === 'PROJECT_MANAGER' && members.some((m) => m.userId === user.id))

  function canRemoveMember(member: ProjectMember) {
    if (!canManageTeam || !user) {
      return false
    }
    if (member.userId === user.id) {
      return false
    }
    if (user.role === 'PROJECT_MANAGER' && member.role !== 'SITE_WORKER') {
      return false
    }
    return true
  }

  const assignableQuery = useQuery({
    queryKey: ['assignable-users', projectId],
    queryFn: () => api.listAssignableUsers(projectId),
    enabled: Boolean(projectId) && canManageTeam,
  })

  const uploadMutation = useMutation({
    mutationFn: (file: File) => api.uploadDocument(projectId, file, title || undefined, category || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] })
      setTitle('')
      setSelectedFileName(null)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    },
  })

  const assignMutation = useMutation({
    mutationFn: (userId: string) => api.assignMember(projectId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] })
      queryClient.invalidateQueries({ queryKey: ['assignable-users', projectId] })
      setSelectedUserId('')
    },
  })

  const removeMutation = useMutation({
    mutationFn: (userId: string) => api.removeMember(projectId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] })
      queryClient.invalidateQueries({ queryKey: ['assignable-users', projectId] })
    },
  })

  const reactivateMutation = useMutation({
    mutationFn: async (assignableUser: User) => {
      await api.updateUser(assignableUser.id, {
        fullName: assignableUser.fullName,
        role: assignableUser.role,
        active: true,
      })
      return api.assignMember(projectId, assignableUser.id)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] })
      queryClient.invalidateQueries({ queryKey: ['assignable-users', projectId] })
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setSelectedUserId('')
    },
  })

  const updateMutation = useMutation({
    mutationFn: () =>
      api.updateProject(projectId, {
        name: editName,
        siteAddress: editSiteAddress || undefined,
        clientName: editClientName || undefined,
        status: editStatus,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] })
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      setIsEditing(false)
    },
  })

  const versionMutation = useMutation({
    mutationFn: ({ documentId, file }: { documentId: string; file: File }) =>
      api.uploadDocumentVersion(documentId, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] })
      queryClient.invalidateQueries({ queryKey: ['document-versions'] })
      setVersionTargetId(null)
      if (versionInputRef.current) {
        versionInputRef.current.value = ''
      }
    },
  })

  const deleteDocumentMutation = useMutation({
    mutationFn: (documentId: string) => api.deleteDocument(documentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] })
      queryClient.invalidateQueries({ queryKey: ['document-versions'] })
    },
  })

  function handleUpload(event: FormEvent) {
    event.preventDefault()
    const file = fileInputRef.current?.files?.[0]
    if (!file) {
      return
    }
    uploadMutation.mutate(file)
  }

  function handleAssignMember(event: FormEvent) {
    event.preventDefault()
    if (!selectedUserId) {
      return
    }
    assignMutation.mutate(selectedUserId)
  }

  function handleRemoveMember(member: ProjectMember) {
    if (!window.confirm(t('team.removeConfirm', { name: member.fullName }))) {
      return
    }
    removeMutation.mutate(member.userId)
  }

  function handleFileChange() {
    const file = fileInputRef.current?.files?.[0]
    setSelectedFileName(file?.name ?? null)
  }

  function startEditing() {
    if (!project) return
    setEditName(project.name)
    setEditSiteAddress(project.siteAddress ?? '')
    setEditClientName(project.clientName ?? '')
    setEditStatus(project.status)
    setIsEditing(true)
  }

  function handleSaveProject(event: FormEvent) {
    event.preventDefault()
    updateMutation.mutate()
  }

  function handleVersionFileChange() {
    const file = versionInputRef.current?.files?.[0]
    if (!file || !versionTargetId) {
      return
    }
    versionMutation.mutate({ documentId: versionTargetId, file })
  }

  function handleDeleteDocument(documentId: string, documentTitle: string) {
    if (!window.confirm(t('drawings.deleteConfirm', { title: documentTitle }))) {
      return
    }
    deleteDocumentMutation.mutate(documentId)
  }

  const assignableUsers = assignableQuery.data ?? []
  const activeAssignableUsers = assignableUsers.filter((assignableUser) => assignableUser.active !== false)
  const inactiveAssignableUsers = assignableUsers.filter((assignableUser) => assignableUser.active === false)

  return (
    <div className="page">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/projects">{t('nav.projects')}</Link>
        <span aria-hidden="true">/</span>
        <span>{project?.name ?? t('project.breadcrumb')}</span>
      </nav>

      {projectQuery.isLoading && (
        <div className="loading-block">
          <span className="spinner" aria-hidden="true" />
          {t('project.loading')}
        </div>
      )}

      {projectQuery.error && <div className="alert alert-error">{t('project.notFound')}</div>}

      {project && (
        <>
          <section className="panel project-hero">
            <div className="project-hero-main">
              <p className="eyebrow">{t('project.overview')}</p>

              {!isEditing ? (
                <>
                  <h1>{project.name}</h1>
                  <dl className="info-grid">
                    <div>
                      <dt>{t('project.siteAddress')}</dt>
                      <dd>{project.siteAddress ?? t('common.notSpecified')}</dd>
                    </div>
                    <div>
                      <dt>{t('project.client')}</dt>
                      <dd>{project.clientName ?? t('common.notSpecified')}</dd>
                    </div>
                    <div>
                      <dt>{t('project.documents')}</dt>
                      <dd>
                        {documents.length === 1
                          ? t('project.pdfCount', { count: documents.length })
                          : t('project.pdfCountPlural', { count: documents.length })}
                      </dd>
                    </div>
                  </dl>
                </>
              ) : (
                <form className="stack-form project-edit-form" onSubmit={handleSaveProject}>
                  <label className="field">
                    <span>{t('create.name')}</span>
                    <input value={editName} onChange={(e) => setEditName(e.target.value)} required />
                  </label>
                  <label className="field">
                    <span>{t('create.siteAddress')}</span>
                    <input
                      value={editSiteAddress}
                      onChange={(e) => setEditSiteAddress(e.target.value)}
                      placeholder={t('create.sitePlaceholder')}
                    />
                  </label>
                  <label className="field">
                    <span>{t('create.clientName')}</span>
                    <input
                      value={editClientName}
                      onChange={(e) => setEditClientName(e.target.value)}
                      placeholder={t('create.clientPlaceholder')}
                    />
                  </label>
                  <label className="field">
                    <span>{t('project.status')}</span>
                    <select value={editStatus} onChange={(e) => setEditStatus(e.target.value as ProjectStatus)}>
                      <option value="DRAFT">{formatProjectStatus('DRAFT', locale)}</option>
                      <option value="ACTIVE">{formatProjectStatus('ACTIVE', locale)}</option>
                      <option value="COMPLETED">{formatProjectStatus('COMPLETED', locale)}</option>
                    </select>
                  </label>

                  {updateMutation.error && (
                    <div className="alert alert-error">
                      {updateMutation.error instanceof Error ? updateMutation.error.message : t('project.saveFailed')}
                    </div>
                  )}

                  <div className="form-actions align-start">
                    <button type="button" className="btn btn-secondary" onClick={() => setIsEditing(false)}>
                      {t('common.cancel')}
                    </button>
                    <button type="submit" className="btn btn-primary" disabled={updateMutation.isPending}>
                      {updateMutation.isPending ? t('common.saving') : t('project.saveChanges')}
                    </button>
                  </div>
                </form>
              )}
            </div>

            <div className="project-hero-actions">
              {!isEditing && <StatusBadge status={project.status} />}
              {canEditProject && !isEditing && (
                <button type="button" className="btn btn-secondary btn-sm" onClick={startEditing}>
                  {t('common.edit')}
                </button>
              )}
            </div>
          </section>

          <section className="panel section-block">
            <div className="section-heading">
              <h2>{t('team.title')}</h2>
              <p>{t('team.count', { count: members.length })}</p>
            </div>

            {canManageTeam && (
              <form className="stack-form" onSubmit={handleAssignMember}>
                <div className="form-row">
                  <label className="field">
                    <span>{t('team.addMember')}</span>
                    <select
                      value={selectedUserId}
                      onChange={(e) => setSelectedUserId(e.target.value)}
                      required
                      disabled={activeAssignableUsers.length === 0}
                    >
                      <option value="">
                        {activeAssignableUsers.length === 0 ? t('team.noActiveUsers') : t('team.selectUser')}
                      </option>
                      {activeAssignableUsers.map((assignableUser) => (
                        <option key={assignableUser.id} value={assignableUser.id}>
                          {assignableUser.fullName} ({assignableUser.email}) · {formatRole(assignableUser.role, locale)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <div className="form-actions align-start">
                    <button
                      type="submit"
                      className="btn btn-primary"
                      disabled={assignMutation.isPending || activeAssignableUsers.length === 0}
                    >
                      {assignMutation.isPending ? t('team.adding') : t('team.addToProject')}
                    </button>
                  </div>
                </div>

                {assignableQuery.error && (
                  <div className="alert alert-error">
                    {assignableQuery.error instanceof Error
                      ? assignableQuery.error.message
                      : t('team.loadPeopleFailed')}
                  </div>
                )}

                {inactiveAssignableUsers.length > 0 && (
                  <div className="alert alert-warning">
                    <p>
                      {inactiveAssignableUsers.length === 1
                        ? t('team.inactiveOne', {
                            names: inactiveAssignableUsers.map((assignableUser) => assignableUser.fullName).join(', '),
                          })
                        : t('team.inactiveMany', {
                            names: inactiveAssignableUsers.map((assignableUser) => assignableUser.fullName).join(', '),
                          })}
                    </p>
                    {user?.role === 'DIRECTOR' ? (
                      <div className="form-actions align-start">
                        {inactiveAssignableUsers.map((assignableUser) => (
                          <button
                            key={assignableUser.id}
                            type="button"
                            className="btn btn-secondary btn-sm"
                            disabled={reactivateMutation.isPending}
                            onClick={() => reactivateMutation.mutate(assignableUser)}
                          >
                            {reactivateMutation.isPending
                              ? t('team.adding')
                              : t('team.reactivateAndAdd', { name: assignableUser.fullName })}
                          </button>
                        ))}
                      </div>
                    ) : (
                      <p>{t('team.askDirector')}</p>
                    )}
                  </div>
                )}

                {assignMutation.error && (
                  <div className="alert alert-error">
                    {assignMutation.error instanceof Error ? assignMutation.error.message : t('team.addFailed')}
                  </div>
                )}

                {reactivateMutation.error && (
                  <div className="alert alert-error">
                    {reactivateMutation.error instanceof Error
                      ? reactivateMutation.error.message
                      : t('team.reactivateFailed')}
                  </div>
                )}
              </form>
            )}

            {removeMutation.error && (
              <div className="alert alert-error">
                {removeMutation.error instanceof Error ? removeMutation.error.message : t('team.removeFailed')}
              </div>
            )}

            {members.length === 0 ? (
              <EmptyState
                title={t('team.emptyTitle')}
                description={canManageTeam ? t('team.emptyManage') : t('team.emptyView')}
              />
            ) : (
              <div className="user-list">
                {members.map((member) => (
                  <div
                    key={member.userId}
                    className={`user-row${canRemoveMember(member) ? ' user-row-member' : ''}`}
                  >
                    <span className="avatar">{getInitials(member.fullName)}</span>
                    <div>
                      <strong>{member.fullName}</strong>
                      <span>{member.email}</span>
                    </div>
                    <span className="role-pill">{formatRole(member.role, locale)}</span>
                    {canRemoveMember(member) && (
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        disabled={removeMutation.isPending}
                        onClick={() => handleRemoveMember(member)}
                      >
                        {removeMutation.isPending && removeMutation.variables === member.userId
                          ? t('team.removing')
                          : t('team.remove')}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {canUpload && (
            <section className="panel section-block">
              <div className="section-heading">
                <h2>{t('upload.title')}</h2>
                <p>{t('upload.description')}</p>
              </div>

              <form className="stack-form" onSubmit={handleUpload}>
                <div className="form-row">
                  <label className="field">
                    <span>{t('upload.drawingTitle')}</span>
                    <input
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      placeholder={t('upload.titlePlaceholder')}
                    />
                  </label>
                  <label className="field">
                    <span>{t('upload.category')}</span>
                    <select value={category} onChange={(e) => setCategory(e.target.value)}>
                      <option value="Plans">{t('category.Plans')}</option>
                      <option value="Sections">{t('category.Sections')}</option>
                      <option value="Elevations">{t('category.Elevations')}</option>
                      <option value="Details">{t('category.Details')}</option>
                      <option value="Other">{t('category.Other')}</option>
                    </select>
                  </label>
                </div>

                <label className="file-drop">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="application/pdf,.pdf"
                    required
                    onChange={handleFileChange}
                  />
                  <span className="file-drop-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
                      <path d="M12 16V4m0 0l-4 4m4-4l4 4M4 20h16" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </span>
                  <strong>{selectedFileName ?? t('upload.choosePdf')}</strong>
                  <span>{t('upload.hint')}</span>
                </label>

                {uploadMutation.error && (
                  <div className="alert alert-error">
                    {uploadMutation.error instanceof Error ? uploadMutation.error.message : t('upload.failed')}
                  </div>
                )}

                <div className="form-actions align-start">
                  <button type="submit" className="btn btn-primary" disabled={uploadMutation.isPending}>
                    {uploadMutation.isPending ? t('upload.uploading') : t('upload.submit')}
                  </button>
                </div>
              </form>
            </section>
          )}

          <section className="section-block">
            <div className="section-heading">
              <h2>{t('drawings.title')}</h2>
              <p>
                {documents.length === 1
                  ? t('drawings.count', { count: documents.length })
                  : t('drawings.countPlural', { count: documents.length })}
              </p>
            </div>

            <label className="search-field search-field-inline">
              <span className="sr-only">{t('drawings.search')}</span>
              <input
                value={documentQuery}
                onChange={(event) => setDocumentQuery(event.target.value)}
                placeholder={t('drawings.searchPlaceholder')}
              />
            </label>

            <input
              ref={versionInputRef}
              type="file"
              accept="application/pdf,.pdf"
              className="sr-only"
              onChange={handleVersionFileChange}
            />

            {documentsQuery.isLoading && (
              <div className="loading-block">
                <span className="spinner" aria-hidden="true" />
                {t('drawings.loading')}
              </div>
            )}

            {!documentsQuery.isLoading && documents.length === 0 && (
              <div className="panel">
                <EmptyState
                  title={documentQuery ? t('drawings.emptyNoMatch') : t('drawings.emptyTitle')}
                  description={
                    documentQuery
                      ? t('drawings.emptyNoMatchDescription')
                      : canUpload
                        ? t('drawings.emptyUpload')
                        : t('drawings.emptyWorker')
                  }
                />
              </div>
            )}

            {(versionMutation.error || deleteDocumentMutation.error) && (
              <div className="alert alert-error">
                {versionMutation.error instanceof Error
                  ? versionMutation.error.message
                  : deleteDocumentMutation.error instanceof Error
                    ? deleteDocumentMutation.error.message
                    : t('drawings.actionFailed')}
              </div>
            )}

            {documents.length > 0 && (
              <div className="table-wrap panel">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>{t('drawings.colTitle')}</th>
                      <th>{t('drawings.colCategory')}</th>
                      <th>{t('drawings.colVersion')}</th>
                      <th>{t('drawings.colPages')}</th>
                      <th>{t('drawings.colSize')}</th>
                      <th aria-label="Actions" />
                    </tr>
                  </thead>
                  <tbody>
                    {documents.map((document) => (
                      <Fragment key={document.id}>
                        <tr>
                          <td>
                            <strong>{document.title}</strong>
                          </td>
                          <td>{formatCategory(document.category, locale)}</td>
                          <td>
                            {t('drawings.latest', { version: document.versionNumber })}
                            {document.versionCount > 1
                              ? ` · ${t('drawings.savedCount', { count: document.versionCount })}`
                              : ''}
                          </td>
                          <td>{document.pageCount}</td>
                          <td>{formatBytes(document.fileSizeBytes)}</td>
                          <td className="table-actions">
                            <Link
                              to={`/projects/${projectId}/documents/${document.id}?version=${document.versionNumber}`}
                              className="btn btn-secondary btn-sm"
                            >
                              {t('drawings.openLatest')}
                            </Link>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() =>
                                setHistoryDocumentId((current) => (current === document.id ? null : document.id))
                              }
                            >
                              {historyDocumentId === document.id
                                ? t('drawings.hideVersions')
                                : t('drawings.olderVersions')}
                            </button>
                            {canUpload && (
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                disabled={versionMutation.isPending}
                                onClick={() => {
                                  setVersionTargetId(document.id)
                                  versionInputRef.current?.click()
                                }}
                              >
                                {t('drawings.newPdf')}
                              </button>
                            )}
                            {canUpload && (
                              <button
                                type="button"
                                className="btn btn-danger btn-sm"
                                disabled={deleteDocumentMutation.isPending}
                                onClick={() => handleDeleteDocument(document.id, document.title)}
                              >
                                {t('common.delete')}
                              </button>
                            )}
                          </td>
                        </tr>
                        {historyDocumentId === document.id && (
                          <tr className="document-history-row">
                            <td colSpan={6}>
                              {documentVersionsQuery.isLoading && (
                                <p className="document-history-empty">{t('drawings.loadingVersions')}</p>
                              )}
                              {documentVersionsQuery.error && (
                                <p className="document-history-empty">{t('drawings.versionsFailed')}</p>
                              )}
                              {documentVersionsQuery.data && documentVersionsQuery.data.length === 0 && (
                                <p className="document-history-empty">{t('drawings.noVersions')}</p>
                              )}
                              {documentVersionsQuery.data && documentVersionsQuery.data.length > 0 && (
                                <ul className="document-history-list">
                                  {documentVersionsQuery.data.map((item) => (
                                    <li key={item.id}>
                                      <Link
                                        to={`/projects/${projectId}/documents/${document.id}?version=${item.versionNumber}`}
                                        className="document-history-link"
                                      >
                                        <strong>{formatDocumentVersionLabel(item, locale)}</strong>
                                        <span>{formatDocumentVersionSource(item.source, locale)}</span>
                                      </Link>
                                    </li>
                                  ))}
                                </ul>
                              )}
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
