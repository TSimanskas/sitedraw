import { describe, expect, it } from 'vitest'
import { formatBytes, formatProjectStatus, formatRole, getInitials, formatAnnotationAuthor, formatDocumentVersionAttribution, formatDocumentVersionSource } from './format'

describe('format helpers', () => {
  it('formats roles and project statuses', () => {
    expect(formatRole('PROJECT_MANAGER')).toBe('Project Manager')
    expect(formatProjectStatus('DRAFT')).toBe('Planning')
    expect(formatRole('PROJECT_MANAGER', 'lt')).toBe('Projekto vadovas')
    expect(formatProjectStatus('DRAFT', 'lt')).toBe('Planuojama')
  })

  it('formats file sizes and initials', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(getInitials('Ada Lovelace')).toBe('AL')
  })

  it('labels saved document versions', () => {
    expect(formatDocumentVersionSource('ORIGINAL')).toBe('Original')
    expect(formatDocumentVersionSource('UPLOAD')).toBe('New PDF')
    expect(formatDocumentVersionSource('MARKUP')).toBe('Saved mark-up')
    expect(formatDocumentVersionAttribution({ source: 'MARKUP', uploadedByName: 'Ada Lovelace' })).toBe(
      'Saved by Ada Lovelace',
    )
    expect(formatDocumentVersionAttribution({ source: 'UPLOAD', uploadedByName: 'Ada Lovelace' })).toBe(
      'Uploaded by Ada Lovelace',
    )
    expect(formatAnnotationAuthor('Ada Lovelace')).toBe('Added by Ada Lovelace')
  })
})
