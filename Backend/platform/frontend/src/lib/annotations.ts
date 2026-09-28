import type { FabricObject } from 'fabric'
import type { AnnotationData, AnnotationItem, AnnotationType } from '../types'
import { readLinePoints, readObjectText } from './annotationFabric'
import { translate, type Locale, type MessageKey } from './i18n'

export function extractAnnotationData(
  type: AnnotationType,
  object: FabricObject,
  canvasWidth: number,
  canvasHeight: number,
  extra: AnnotationData = {},
): AnnotationData {
  const base = { ...extra }

  if (type === 'PEN' && 'path' in object) {
    const path = object as FabricObject & { path?: Array<Array<string | number>> }
    const points = flattenPathPoints(path.path ?? [], canvasWidth, canvasHeight)
    return {
      ...base,
      points,
      stroke: String(object.stroke ?? '#ef4444'),
      strokeWidth: Number(object.strokeWidth ?? 2),
    }
  }

  if (type === 'TEXT' || type === 'STAMP') {
    return {
      ...base,
      left: Number(object.left ?? 0) / canvasWidth,
      top: Number(object.top ?? 0) / canvasHeight,
      text: String(readObjectText(object) ?? extra.text ?? (type === 'STAMP' ? 'APPROVED' : 'Note')),
      fill: String(object.fill ?? (type === 'STAMP' ? '#16a34a' : '#111827')),
      fontSize: Number((object as FabricObject & { fontSize?: number }).fontSize ?? 16),
      angle: Number(object.angle ?? 0),
    }
  }

  if (type === 'ARROW' || type === 'MEASURE') {
    const points = readLinePoints(object)
    return {
      ...base,
      x1: Number(points?.x1 ?? extra.x1 ?? 0) / canvasWidth,
      y1: Number(points?.y1 ?? extra.y1 ?? 0) / canvasHeight,
      x2: Number(points?.x2 ?? extra.x2 ?? 0) / canvasWidth,
      y2: Number(points?.y2 ?? extra.y2 ?? 0) / canvasHeight,
      stroke: String(object.stroke ?? extra.stroke ?? (type === 'MEASURE' ? '#2563eb' : '#ef4444')),
      strokeWidth: Number(object.strokeWidth ?? extra.strokeWidth ?? 2),
      label: String(extra.label ?? ''),
    }
  }

  const width = Number(object.width ?? 0) * Number(object.scaleX ?? 1)
  const height = Number(object.height ?? 0) * Number(object.scaleY ?? 1)

  return {
    ...base,
    left: Number(object.left ?? 0) / canvasWidth,
    top: Number(object.top ?? 0) / canvasHeight,
    width: width / canvasWidth,
    height: height / canvasHeight,
    fill: String(object.fill ?? 'rgba(255,255,0,0.35)'),
    ...(object.stroke ? { stroke: String(object.stroke) } : {}),
    ...(object.strokeWidth ? { strokeWidth: Number(object.strokeWidth) } : {}),
    angle: Number(object.angle ?? 0),
  }
}

function flattenPathPoints(
  path: Array<Array<string | number>>,
  canvasWidth: number,
  canvasHeight: number,
): number[][] {
  const points: number[][] = []

  for (const segment of path) {
    const command = String(segment[0])
    if (command === 'M' || command === 'L') {
      points.push([
        Number(segment[1]) / canvasWidth,
        Number(segment[2]) / canvasHeight,
      ])
    }
  }

  return points
}

export function canEditAnnotation(annotation: AnnotationItem, userId: string, role: string): boolean {
  if (role === 'DIRECTOR' || role === 'PROJECT_MANAGER') {
    return true
  }
  return annotation.createdById === userId
}

const REVISION_KEYS: Record<string, MessageKey> = {
  CREATE: 'revision.create',
  UPDATE: 'revision.update',
  DELETE: 'revision.delete',
  ROLLBACK: 'revision.rollback',
}

const AUDIT_KEYS: Record<string, MessageKey> = {
  ANNOTATION_CREATED: 'audit.annotationCreated',
  ANNOTATION_UPDATED: 'audit.annotationUpdated',
  ANNOTATION_DELETED: 'audit.annotationDeleted',
  ANNOTATION_ROLLBACK: 'audit.annotationRollback',
  PROJECT_CREATED: 'audit.projectCreated',
  PROJECT_UPDATED: 'audit.projectUpdated',
  PROJECT_DELETED: 'audit.projectDeleted',
  DOCUMENT_UPLOADED: 'audit.documentUploaded',
  DOCUMENT_VERSION_SAVED: 'audit.documentVersionSaved',
  DOCUMENT_DELETED: 'audit.documentDeleted',
}

export function formatRevisionAction(action: string, locale: Locale = 'en'): string {
  const key = REVISION_KEYS[action]
  return key ? translate(locale, key) : action
}

export function formatAuditAction(action: string, locale: Locale = 'en'): string {
  const key = AUDIT_KEYS[action]
  if (key) {
    return translate(locale, key)
  }
  return action
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}
