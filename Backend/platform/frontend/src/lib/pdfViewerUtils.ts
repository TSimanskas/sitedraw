import type { AnnotationItem } from '../types'

export const LOCAL_ID_PREFIX = 'local-'

export type LocalUndoEntry =
  | { kind: 'create'; ids: string[] }
  | { kind: 'update'; id: string; before: AnnotationItem['data'] }
  | { kind: 'delete'; items: AnnotationItem[] }

export function isLocalAnnotationId(id: string) {
  return id.startsWith(LOCAL_ID_PREFIX)
}

export function createLocalAnnotationId() {
  return `${LOCAL_ID_PREFIX}${crypto.randomUUID()}`
}

export function annotationsDifferFromSaved(current: AnnotationItem[], saved: AnnotationItem[]) {
  if (current.length !== saved.length) {
    return true
  }

  const savedById = new Map(saved.map((item) => [item.id, item]))
  if (saved.some((item) => !current.some((entry) => entry.id === item.id))) {
    return true
  }

  return current.some((item) => {
    if (isLocalAnnotationId(item.id)) {
      return true
    }
    const original = savedById.get(item.id)
    return !original || JSON.stringify(original.data) !== JSON.stringify(item.data)
  })
}
