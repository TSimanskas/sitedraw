import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PageHeader } from '../components/PageHeader'
import { api } from '../lib/api'
import { formatRole, getInitials } from '../lib/format'
import { getUser } from '../lib/auth'
import { useI18n } from '../lib/LanguageContext'
import type { CreateUserPayload, User, UserRole } from '../types'

export function UsersPage() {
  const queryClient = useQueryClient()
  const currentUser = getUser()
  const { t, locale } = useI18n()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState<CreateUserPayload['role']>('PROJECT_MANAGER')
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [editingUserId, setEditingUserId] = useState<string | null>(null)
  const [editFullName, setEditFullName] = useState('')
  const [editRole, setEditRole] = useState<UserRole>('SITE_WORKER')
  const [resetUserId, setResetUserId] = useState<string | null>(null)
  const [resetPassword, setResetPassword] = useState('')

  const usersQuery = useQuery({
    queryKey: ['users'],
    queryFn: api.listUsers,
  })

  const createMutation = useMutation({
    mutationFn: api.createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      queryClient.invalidateQueries({ queryKey: ['assignable-users'] })
      setEmail('')
      setPassword('')
      setFullName('')
      setSuccessMessage(t('users.created'))
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({
      userId,
      fullName: name,
      role: nextRole,
      active,
    }: {
      userId: string
      fullName: string
      role: UserRole
      active: boolean
    }) => api.updateUser(userId, { fullName: name, role: nextRole, active }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      queryClient.invalidateQueries({ queryKey: ['assignable-users'] })
      setEditingUserId(null)
      setSuccessMessage(t('users.updated'))
    },
  })

  const resetMutation = useMutation({
    mutationFn: ({ userId, password: nextPassword }: { userId: string; password: string }) =>
      api.resetUserPassword(userId, nextPassword),
    onSuccess: () => {
      setResetUserId(null)
      setResetPassword('')
      setSuccessMessage(t('users.passwordReset'))
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSuccessMessage(null)
    createMutation.mutate({ email, password, fullName, role })
  }

  function startEdit(user: User) {
    setEditingUserId(user.id)
    setEditFullName(user.fullName)
    setEditRole(user.role)
    setResetUserId(null)
    setSuccessMessage(null)
  }

  const users = usersQuery.data ?? []
  const actionError = createMutation.error ?? updateMutation.error ?? resetMutation.error

  return (
    <div className="page">
      <PageHeader eyebrow={t('users.eyebrow')} title={t('users.title')} description={t('users.description')} />

      <div className="two-column">
        <section className="panel section-block">
          <div className="section-heading">
            <h2>{t('users.addTitle')}</h2>
            <p>{t('users.addDescription')}</p>
          </div>

          <form className="stack-form" onSubmit={handleSubmit}>
            <label className="field">
              <span>{t('users.fullName')}</span>
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </label>

            <label className="field">
              <span>{t('users.email')}</span>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </label>

            <label className="field">
              <span>{t('users.password')}</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                placeholder={t('users.passwordHint')}
              />
            </label>

            <label className="field">
              <span>{t('users.role')}</span>
              <select value={role} onChange={(e) => setRole(e.target.value as CreateUserPayload['role'])}>
                <option value="PROJECT_MANAGER">{formatRole('PROJECT_MANAGER', locale)}</option>
                <option value="SITE_WORKER">{formatRole('SITE_WORKER', locale)}</option>
              </select>
            </label>

            {actionError && (
              <div className="alert alert-error">
                {actionError instanceof Error ? actionError.message : t('users.updateFailed')}
              </div>
            )}

            {successMessage && <div className="alert alert-success">{successMessage}</div>}

            <button type="submit" className="btn btn-primary" disabled={createMutation.isPending}>
              {createMutation.isPending ? t('users.creating') : t('users.create')}
            </button>
          </form>
        </section>

        <section className="section-block">
          <div className="section-heading">
            <h2>{t('users.all')}</h2>
            <p>
              {users.length === 1
                ? t('users.accountCount', { count: users.length })
                : t('users.accountCountPlural', { count: users.length })}
            </p>
          </div>

          {usersQuery.isLoading && (
            <div className="loading-block">
              <span className="spinner" aria-hidden="true" />
              {t('users.loading')}
            </div>
          )}

          <div className="panel user-list">
            {users.map((user) => {
              const isDirector = user.role === 'DIRECTOR'
              const isSelf = user.id === currentUser?.id
              const isEditing = editingUserId === user.id
              const isResetting = resetUserId === user.id

              return (
                <div key={user.id} className="user-row user-row-manage">
                  <span className="avatar">{getInitials(user.fullName)}</span>
                  <div>
                    {isEditing ? (
                      <form
                        className="user-edit-form"
                        onSubmit={(event) => {
                          event.preventDefault()
                          updateMutation.mutate({
                            userId: user.id,
                            fullName: editFullName,
                            role: editRole,
                            active: user.active !== false,
                          })
                        }}
                      >
                        <input
                          value={editFullName}
                          onChange={(event) => setEditFullName(event.target.value)}
                          required
                        />
                        {!isDirector && (
                          <select
                            value={editRole}
                            onChange={(event) => setEditRole(event.target.value as UserRole)}
                          >
                            <option value="PROJECT_MANAGER">{formatRole('PROJECT_MANAGER', locale)}</option>
                            <option value="SITE_WORKER">{formatRole('SITE_WORKER', locale)}</option>
                          </select>
                        )}
                        <div className="user-row-actions">
                          <button type="submit" className="btn btn-primary btn-sm" disabled={updateMutation.isPending}>
                            {t('common.save')}
                          </button>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => setEditingUserId(null)}
                          >
                            {t('common.cancel')}
                          </button>
                        </div>
                      </form>
                    ) : (
                      <>
                        <strong>
                          {user.fullName}
                          {user.active === false ? ` ${t('users.inactive')}` : ''}
                        </strong>
                        <span>{user.email}</span>
                      </>
                    )}

                    {isResetting && (
                      <form
                        className="user-edit-form"
                        onSubmit={(event) => {
                          event.preventDefault()
                          resetMutation.mutate({ userId: user.id, password: resetPassword })
                        }}
                      >
                        <input
                          type="password"
                          value={resetPassword}
                          onChange={(event) => setResetPassword(event.target.value)}
                          minLength={8}
                          placeholder={t('users.newPassword')}
                          required
                        />
                        <div className="user-row-actions">
                          <button type="submit" className="btn btn-primary btn-sm" disabled={resetMutation.isPending}>
                            {t('users.setPassword')}
                          </button>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => {
                              setResetUserId(null)
                              setResetPassword('')
                            }}
                          >
                            {t('common.cancel')}
                          </button>
                        </div>
                      </form>
                    )}
                  </div>
                  <span className="role-pill">{formatRole(user.role, locale)}</span>
                  {!isEditing && (
                    <div className="user-row-actions">
                      {!isDirector && (
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => startEdit(user)}>
                          {t('common.edit')}
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => {
                          setResetUserId(user.id)
                          setResetPassword('')
                          setEditingUserId(null)
                          setSuccessMessage(null)
                        }}
                      >
                        {t('users.resetPassword')}
                      </button>
                      {!isDirector && !isSelf && (
                        <button
                          type="button"
                          className="btn btn-danger btn-sm"
                          disabled={updateMutation.isPending}
                          onClick={() => {
                            const nextActive = user.active === false
                            if (
                              !nextActive &&
                              !window.confirm(t('users.deactivateConfirm', { name: user.fullName }))
                            ) {
                              return
                            }
                            updateMutation.mutate({
                              userId: user.id,
                              fullName: user.fullName,
                              role: user.role,
                              active: nextActive,
                            })
                          }}
                        >
                          {user.active === false ? t('users.reactivate') : t('users.deactivate')}
                        </button>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </section>
      </div>
    </div>
  )
}
