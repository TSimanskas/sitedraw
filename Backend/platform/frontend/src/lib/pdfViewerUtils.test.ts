import { describe, expect, it } from 'vitest'
import { annotationsDifferFromSaved, isLocalAnnotationId } from './pdfViewerUtils'
import type { AnnotationItem } from '../types'

function item(id: string, left = 0.1): AnnotationItem {
  return {
    id,
    documentVersionId: 'ver-1',
    documentId: 'doc-1',
    pageNumber: 1,
    type: 'TEXT',
    data: { left, top: 0.2 },
    createdById: 'user-1',
    createdByName: 'Ada',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deleted: false,
  }
}

describe('pdf viewer helpers', () => {
  it('detects local annotation ids', () => {
    expect(isLocalAnnotationId('local-abc')).toBe(true)
    expect(isLocalAnnotationId('saved-abc')).toBe(false)
  })

  it('detects unsaved annotation changes', () => {
    const saved = [item('ann-1')]
    expect(annotationsDifferFromSaved(saved, saved)).toBe(false)
    expect(annotationsDifferFromSaved([item('ann-1', 0.5)], saved)).toBe(true)
    expect(annotationsDifferFromSaved([item('local-1')], saved)).toBe(true)
  })
})
