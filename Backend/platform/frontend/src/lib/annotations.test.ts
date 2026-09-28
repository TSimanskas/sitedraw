import { describe, expect, it } from 'vitest'
import { canEditAnnotation, formatAuditAction, formatRevisionAction } from './annotations'
import type { AnnotationItem } from '../types'

function annotation(createdById: string): AnnotationItem {
  return {
    id: 'ann-1',
    documentVersionId: 'ver-1',
    documentId: 'doc-1',
    pageNumber: 1,
    type: 'TEXT',
    data: {},
    createdById,
    createdByName: 'Worker',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deleted: false,
  }
}

describe('annotation helpers', () => {
  it('lets directors and managers edit any annotation', () => {
    const item = annotation('worker-1')
    expect(canEditAnnotation(item, 'someone-else', 'DIRECTOR')).toBe(true)
    expect(canEditAnnotation(item, 'someone-else', 'PROJECT_MANAGER')).toBe(true)
  })

  it('lets site workers edit only their own annotations', () => {
    const item = annotation('worker-1')
    expect(canEditAnnotation(item, 'worker-1', 'SITE_WORKER')).toBe(true)
    expect(canEditAnnotation(item, 'worker-2', 'SITE_WORKER')).toBe(false)
  })

  it('formats revision and audit actions', () => {
    expect(formatRevisionAction('ROLLBACK')).toBe('Rolled back')
    expect(formatAuditAction('DOCUMENT_DELETED')).toBe('Document deleted')
  })
})
