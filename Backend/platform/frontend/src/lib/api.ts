import { clearSession, getToken } from './auth'
import type {
  AnnotationItem,
  AnnotationRevision,
  AuditEventItem,
  AuthResponse,
  CalibrationSettings,
  CreateAnnotationPayload,
  CreateProjectPayload,
  CreateUserPayload,
  DocumentItem,
  DocumentVersionItem,
  Project,
  SaveMarkupAnnotationPayload,
  UpdateAnnotationPayload,
  UpdateProjectPayload,
  UpdateUserPayload,
  User,
} from '../types'

const API_BASE = import.meta.env.VITE_API_URL ?? ''

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function parseError(response: Response): Promise<string> {
  try {
    const body = await response.json()
    return body.detail ?? body.title ?? response.statusText
  } catch {
    return response.statusText
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getToken()

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  })

  if (response.status === 401) {
    clearSession()
    window.location.href = '/login'
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    throw new ApiError(response.status, await parseError(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const api = {
  login(email: string, password: string) {
    return request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
  },

  me() {
    return request<User>('/api/auth/me')
  },

  listProjects(query?: string) {
    const search = query?.trim() ? `?q=${encodeURIComponent(query.trim())}` : ''
    return request<Project[]>(`/api/projects${search}`)
  },

  getProject(projectId: string) {
    return request<Project>(`/api/projects/${projectId}`)
  },

  createProject(payload: CreateProjectPayload) {
    return request<Project>('/api/projects', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  updateProject(projectId: string, payload: UpdateProjectPayload) {
    return request<Project>(`/api/projects/${projectId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },

  deleteProject(projectId: string) {
    return request<void>(`/api/projects/${projectId}`, { method: 'DELETE' })
  },

  assignMember(projectId: string, userId: string) {
    return request<Project>(`/api/projects/${projectId}/members`, {
      method: 'POST',
      body: JSON.stringify({ userId }),
    })
  },

  removeMember(projectId: string, userId: string) {
    return request<Project>(`/api/projects/${projectId}/members/${userId}`, {
      method: 'DELETE',
    })
  },

  listAssignableUsers(projectId: string) {
    return request<User[]>(`/api/projects/${projectId}/assignable-users`)
  },

  listDocuments(projectId: string, query?: string) {
    const search = query?.trim() ? `?q=${encodeURIComponent(query.trim())}` : ''
    return request<DocumentItem[]>(`/api/projects/${projectId}/documents${search}`)
  },

  uploadDocument(projectId: string, file: File, title?: string, category?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (title) {
      formData.append('title', title)
    }
    if (category) {
      formData.append('category', category)
    }

    return request<DocumentItem>(`/api/projects/${projectId}/documents`, {
      method: 'POST',
      body: formData,
    })
  },

  listDocumentVersions(documentId: string) {
    return request<DocumentVersionItem[]>(`/api/documents/${documentId}/versions`)
  },

  saveMarkupVersion(documentId: string, versionNumber: number, payload: { annotations: SaveMarkupAnnotationPayload[] }) {
    return request<DocumentVersionItem>(`/api/documents/${documentId}/versions/${versionNumber}/markup-versions`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  uploadDocumentVersion(documentId: string, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request<DocumentItem>(`/api/documents/${documentId}/versions`, {
      method: 'POST',
      body: formData,
    })
  },

  deleteDocument(documentId: string) {
    return request<void>(`/api/documents/${documentId}`, { method: 'DELETE' })
  },

  listUsers() {
    return request<User[]>('/api/users')
  },

  createUser(payload: CreateUserPayload) {
    return request<User>('/api/users', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  updateUser(userId: string, payload: UpdateUserPayload) {
    return request<User>(`/api/users/${userId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },

  resetUserPassword(userId: string, password: string) {
    return request<void>(`/api/users/${userId}/password`, {
      method: 'PUT',
      body: JSON.stringify({ password }),
    })
  },

  changePassword(currentPassword: string, newPassword: string) {
    return request<void>('/api/auth/password', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    })
  },

  documentFileUrl(documentId: string, versionNumber: number) {
    return `${API_BASE}/api/documents/${documentId}/versions/${versionNumber}/file`
  },

  listAnnotations(documentId: string, versionNumber: number, page?: number) {
    const query = page != null ? `?page=${page}` : ''
    return request<AnnotationItem[]>(
      `/api/documents/${documentId}/versions/${versionNumber}/annotations${query}`,
    )
  },

  createAnnotation(documentId: string, versionNumber: number, payload: CreateAnnotationPayload) {
    return request<AnnotationItem>(`/api/documents/${documentId}/versions/${versionNumber}/annotations`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  updateAnnotation(annotationId: string, payload: UpdateAnnotationPayload) {
    return request<AnnotationItem>(`/api/annotations/${annotationId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },

  deleteAnnotation(annotationId: string) {
    return request<void>(`/api/annotations/${annotationId}`, { method: 'DELETE' })
  },

  listAnnotationRevisions(annotationId: string) {
    return request<AnnotationRevision[]>(`/api/annotations/${annotationId}/revisions`)
  },

  rollbackAnnotation(annotationId: string, revisionId?: string) {
    return request<AnnotationItem>(`/api/annotations/${annotationId}/rollback`, {
      method: 'POST',
      body: JSON.stringify(revisionId ? { revisionId } : {}),
    })
  },

  getCalibration(documentId: string, versionNumber: number) {
    return request<CalibrationSettings>(`/api/documents/${documentId}/versions/${versionNumber}/calibration`)
  },

  updateCalibration(documentId: string, versionNumber: number, pixelsPerUnit: number, unitLabel: string) {
    return request<CalibrationSettings>(`/api/documents/${documentId}/versions/${versionNumber}/calibration`, {
      method: 'PUT',
      body: JSON.stringify({ pixelsPerUnit, unitLabel }),
    })
  },

  listAuditEvents(params: { projectId?: string; documentVersionId?: string }) {
    const search = new URLSearchParams()
    if (params.projectId) {
      search.set('projectId', params.projectId)
    }
    if (params.documentVersionId) {
      search.set('documentVersionId', params.documentVersionId)
    }
    const query = search.toString()
    return request<AuditEventItem[]>(`/api/audit-events${query ? `?${query}` : ''}`)
  },
}
