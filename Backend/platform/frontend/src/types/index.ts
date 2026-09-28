export type UserRole = 'DIRECTOR' | 'PROJECT_MANAGER' | 'SITE_WORKER'

export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED'

export interface User {
  id: string
  email: string
  fullName: string
  role: UserRole
  active?: boolean
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresInMs: number
  user: User
}

export interface Project {
  id: string
  name: string
  siteAddress: string | null
  clientName: string | null
  status: ProjectStatus
  startDate: string | null
  endDate: string | null
  createdById: string | null
  createdByName: string | null
  members: ProjectMember[]
}

export interface DocumentItem {
  id: string
  projectId: string
  title: string
  category: string | null
  versionNumber: number
  versionCount: number
  pageCount: number
  fileSizeBytes: number
  uploadedById: string | null
  uploadedByName: string | null
  uploadedAt: string
}

export type DocumentVersionSource = 'ORIGINAL' | 'UPLOAD' | 'MARKUP'

export interface DocumentVersionItem {
  id: string
  documentId: string
  versionNumber: number
  pageCount: number
  fileSizeBytes: number
  uploadedById: string | null
  uploadedByName: string | null
  uploadedAt: string
  source?: DocumentVersionSource
}

export interface CreateProjectPayload {
  name: string
  siteAddress?: string
  clientName?: string
  startDate?: string
  endDate?: string
}

export interface UpdateProjectPayload {
  name: string
  siteAddress?: string
  clientName?: string
  status: ProjectStatus
  startDate?: string
  endDate?: string
}

export interface CreateUserPayload {
  email: string
  password: string
  fullName: string
  role: 'PROJECT_MANAGER' | 'SITE_WORKER'
}

export interface UpdateUserPayload {
  fullName: string
  role: UserRole
  active: boolean
}

export interface ProjectMember {
  userId: string
  fullName: string
  email: string
  role: UserRole
  assignedAt: string
}

export type AnnotationType = 'HIGHLIGHT' | 'ARROW' | 'TEXT' | 'STAMP' | 'MEASURE' | 'PEN'

export type RevisionAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'ROLLBACK'

export type AuditAction =
  | 'ANNOTATION_CREATED'
  | 'ANNOTATION_UPDATED'
  | 'ANNOTATION_DELETED'
  | 'ANNOTATION_ROLLBACK'
  | 'PROJECT_CREATED'
  | 'PROJECT_UPDATED'
  | 'PROJECT_DELETED'
  | 'DOCUMENT_UPLOADED'
  | 'DOCUMENT_VERSION_SAVED'
  | 'DOCUMENT_DELETED'

export type AnnotationData = Record<string, number | string | number[][]>

export interface AnnotationItem {
  id: string
  documentVersionId: string
  documentId: string
  pageNumber: number
  type: AnnotationType
  data: AnnotationData
  createdById: string
  createdByName: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}

export interface AnnotationRevision {
  id: string
  annotationId: string
  action: RevisionAction
  beforeState: Record<string, unknown> | null
  afterState: Record<string, unknown> | null
  changedById: string
  changedByName: string
  changedAt: string
}

export interface AuditEventItem {
  id: string
  userId: string | null
  userName: string | null
  action: AuditAction
  entityType: string
  entityId: string | null
  projectId: string | null
  documentVersionId: string | null
  beforeState: Record<string, unknown> | null
  afterState: Record<string, unknown> | null
  createdAt: string
}

export interface CalibrationSettings {
  documentVersionId: string
  pixelsPerUnit: number | null
  unitLabel: string | null
}

export interface CreateAnnotationPayload {
  pageNumber: number
  type: AnnotationType
  data: AnnotationData
}

export interface SaveMarkupAnnotationPayload extends CreateAnnotationPayload {
  sourceAnnotationId?: string
}

export interface UpdateAnnotationPayload {
  data: AnnotationData
}

export type ViewerTool = 'select' | 'pan' | AnnotationType
