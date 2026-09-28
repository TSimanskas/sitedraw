import {
  Canvas,
  Circle,
  FabricObject,
  Group,
  IText,
  Line,
  Path,
  PencilBrush,
  Rect,
  Triangle,
} from 'fabric'
import type { AnnotationData, AnnotationItem, AnnotationType } from '../types'

export const ANNOTATION_ID_KEY = 'annotationId'

export function createFabricCanvas(element: HTMLCanvasElement, width: number, height: number): Canvas {
  return new Canvas(element, {
    width,
    height,
    selection: true,
    preserveObjectStacking: true,
  })
}

export function setDrawingTool(canvas: Canvas, type: AnnotationType | 'select' | 'pan'): void {
  canvas.isDrawingMode = type === 'PEN'
  canvas.selection = false
  canvas.skipTargetFind = type !== 'select'
  canvas.defaultCursor = type === 'pan' || type === 'select' ? 'grab' : 'crosshair'

  if (type === 'PEN') {
    const brush = new PencilBrush(canvas)
    brush.color = '#ef4444'
    brush.width = 2
    canvas.freeDrawingBrush = brush
  }
}

export function buildFabricObject(
  annotation: AnnotationItem,
  canvasWidth: number,
  canvasHeight: number,
): FabricObject {
  const object = buildObjectForType(annotation.type, annotation.data, canvasWidth, canvasHeight)
  makeObjectSelectable(object)
  object.set({
    [ANNOTATION_ID_KEY]: annotation.id,
  })
  return object
}

export function buildObjectForType(
  type: AnnotationType,
  data: AnnotationData,
  canvasWidth: number,
  canvasHeight: number,
): FabricObject {
  switch (type) {
    case 'HIGHLIGHT':
      return new Rect({
        left: Number(data.left) * canvasWidth,
        top: Number(data.top) * canvasHeight,
        width: Number(data.width) * canvasWidth,
        height: Number(data.height) * canvasHeight,
        fill: String(data.fill ?? 'rgba(255,255,0,0.35)'),
        stroke: data.stroke ? String(data.stroke) : undefined,
        strokeWidth: data.strokeWidth ? Number(data.strokeWidth) : 0,
        angle: Number(data.angle ?? 0),
      })
    case 'TEXT':
      return new IText(String(data.text ?? 'Note'), {
        left: Number(data.left) * canvasWidth,
        top: Number(data.top) * canvasHeight,
        fill: String(data.fill ?? '#111827'),
        fontSize: Number(data.fontSize ?? 16),
        angle: Number(data.angle ?? 0),
      })
    case 'STAMP':
      return buildStamp(String(data.text ?? 'APPROVED'), data, canvasWidth, canvasHeight)
    case 'ARROW':
      return buildArrow(data, canvasWidth, canvasHeight)
    case 'MEASURE':
      return buildMeasure(data, canvasWidth, canvasHeight)
    case 'PEN':
      return buildPenPath(data, canvasWidth, canvasHeight)
    default:
      return new Rect({
        left: 0,
        top: 0,
        width: 10,
        height: 10,
        fill: 'rgba(255,255,0,0.35)',
      })
  }
}

function buildStamp(
  label: string,
  data: AnnotationData,
  canvasWidth: number,
  canvasHeight: number,
): Group {
  const fill = String(data.fill ?? '#16a34a')
  const rect = new Rect({
    width: 120,
    height: 36,
    fill: 'transparent',
    stroke: fill,
    strokeWidth: 2,
    rx: 4,
    ry: 4,
  })
  const text = new IText(label, {
    left: 16,
    top: 8,
    fill,
    fontSize: 16,
    fontWeight: 'bold',
  })

  const group = new Group([rect, text], {
    left: Number(data.left) * canvasWidth,
    top: Number(data.top) * canvasHeight,
    angle: Number(data.angle ?? 0),
  })
  freezeChildTargets(group)
  return group
}

function buildArrow(data: AnnotationData, canvasWidth: number, canvasHeight: number): Group {
  const x1 = Number(data.x1) * canvasWidth
  const y1 = Number(data.y1) * canvasHeight
  const x2 = Number(data.x2) * canvasWidth
  const y2 = Number(data.y2) * canvasHeight
  const stroke = String(data.stroke ?? '#ef4444')

  const line = new Line([x1, y1, x2, y2], {
    stroke,
    strokeWidth: Number(data.strokeWidth ?? 2),
  })

  const angle = Math.atan2(y2 - y1, x2 - x1)
  const head = new Triangle({
    left: x2,
    top: y2,
    width: 12,
    height: 12,
    fill: stroke,
    angle: (angle * 180) / Math.PI + 90,
    originX: 'center',
    originY: 'center',
  })

  const group = new Group([line, head])
  freezeChildTargets(group)
  stampLinePoints(group, x1, y1, x2, y2)
  return group
}

function buildMeasure(data: AnnotationData, canvasWidth: number, canvasHeight: number): Group {
  const x1 = Number(data.x1) * canvasWidth
  const y1 = Number(data.y1) * canvasHeight
  const x2 = Number(data.x2) * canvasWidth
  const y2 = Number(data.y2) * canvasHeight
  const stroke = String(data.stroke ?? '#2563eb')

  const line = new Line([x1, y1, x2, y2], {
    stroke,
    strokeWidth: Number(data.strokeWidth ?? 2),
  })

  const label = new IText(String(data.label ?? ''), {
    left: (x1 + x2) / 2,
    top: (y1 + y2) / 2 - 16,
    fill: stroke,
    fontSize: 14,
    backgroundColor: 'rgba(255,255,255,0.85)',
  })

  const group = new Group([line, label])
  freezeChildTargets(group)
  stampLinePoints(group, x1, y1, x2, y2)
  return group
}

function buildPenPath(data: AnnotationData, canvasWidth: number, canvasHeight: number): Path {
  const points = (data.points as number[][] | undefined) ?? []
  const pathData = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point[0] * canvasWidth} ${point[1] * canvasHeight}`)
    .join(' ')

  return new Path(pathData || 'M 0 0 L 1 1', {
    fill: '',
    stroke: String(data.stroke ?? '#ef4444'),
    strokeWidth: Number(data.strokeWidth ?? 2),
  })
}

export function createPreviewShape(
  type: AnnotationType,
  startX: number,
  startY: number,
  endX: number,
  endY: number,
): FabricObject | null {
  switch (type) {
    case 'HIGHLIGHT': {
      const highlight = new Rect({
        left: Math.min(startX, endX),
        top: Math.min(startY, endY),
        width: Math.abs(endX - startX),
        height: Math.abs(endY - startY),
        fill: 'rgba(255,255,0,0.35)',
      })
      makeObjectSelectable(highlight)
      return highlight
    }
    case 'TEXT': {
      const note = new IText('Note', {
        left: startX,
        top: startY,
        fill: '#111827',
        fontSize: 16,
      })
      makeObjectSelectable(note)
      return note
    }
    case 'STAMP': {
      const stamp = buildStamp('APPROVED', { left: 0, top: 0, fill: '#16a34a' }, 1, 1)
      makeObjectSelectable(stamp)
      return stamp
    }
    case 'ARROW': {
      const arrow = buildArrow(
        {
          x1: startX,
          y1: startY,
          x2: endX,
          y2: endY,
        },
        1,
        1,
      )
      makeObjectSelectable(arrow)
      return arrow
    }
    case 'MEASURE': {
      const measure = buildMeasure(
        {
          x1: startX,
          y1: startY,
          x2: endX,
          y2: endY,
          label: '',
        },
        1,
        1,
      )
      makeObjectSelectable(measure)
      return measure
    }
    default:
      return null
  }
}

export function computeMeasureLabel(
  x1: number,
  y1: number,
  x2: number,
  y2: number,
  pixelsPerUnit: number | null,
  unitLabel: string | null,
): string {
  const distance = Math.hypot(x2 - x1, y2 - y1)
  if (!pixelsPerUnit || !unitLabel) {
    return `${distance.toFixed(1)} px`
  }
  return `${(distance / pixelsPerUnit).toFixed(2)} ${unitLabel}`
}

export function getAnnotationId(object: FabricObject): string | undefined {
  return object.get(ANNOTATION_ID_KEY) as string | undefined
}

export function setAnnotationId(object: FabricObject, annotationId: string): void {
  object.set(ANNOTATION_ID_KEY, annotationId)
}

export function getAnnotatedObject(object: FabricObject | undefined | null): FabricObject | null {
  let current: FabricObject | null | undefined = object
  const seen = new Set<FabricObject>()

  while (current && !seen.has(current)) {
    seen.add(current)
    if (getAnnotationId(current)) {
      return current
    }

    const grouped = current as FabricObject & { group?: FabricObject; parent?: FabricObject }
    current = grouped.group ?? grouped.parent ?? null
  }

  return null
}

export function makeObjectSelectable(object: FabricObject): void {
  object.set({
    selectable: true,
    evented: true,
    hasControls: true,
    hasBorders: true,
    hoverCursor: 'move',
    subTargetCheck: false,
  })
}

export function freezeChildTargets(object: FabricObject): void {
  if (!('getObjects' in object) || typeof (object as Group).getObjects !== 'function') {
    return
  }

  for (const child of (object as Group).getObjects()) {
    child.set({
      selectable: false,
      evented: false,
      hasControls: false,
    })
    if ('editable' in child) {
      ;(child as IText).editable = false
    }
  }
}

export function stampLinePoints(object: FabricObject, x1: number, y1: number, x2: number, y2: number): void {
  object.set({
    lineX1: x1,
    lineY1: y1,
    lineX2: x2,
    lineY2: y2,
    originLeft: object.left,
    originTop: object.top,
  })
}

export function readLinePoints(object: FabricObject): { x1: number; y1: number; x2: number; y2: number } | null {
  const storedX1 = object.get('lineX1') as number | undefined
  const storedY1 = object.get('lineY1') as number | undefined
  const storedX2 = object.get('lineX2') as number | undefined
  const storedY2 = object.get('lineY2') as number | undefined

  if (storedX1 != null && storedY1 != null && storedX2 != null && storedY2 != null) {
    const dx = Number(object.left ?? 0) - Number(object.get('originLeft') ?? object.left ?? 0)
    const dy = Number(object.top ?? 0) - Number(object.get('originTop') ?? object.top ?? 0)
    return {
      x1: storedX1 + dx,
      y1: storedY1 + dy,
      x2: storedX2 + dx,
      y2: storedY2 + dy,
    }
  }

  const line = object as FabricObject & { x1?: number; y1?: number; x2?: number; y2?: number }
  if (line.x1 != null && line.y1 != null && line.x2 != null && line.y2 != null) {
    return {
      x1: Number(line.x1),
      y1: Number(line.y1),
      x2: Number(line.x2),
      y2: Number(line.y2),
    }
  }

  return null
}

export function readObjectText(object: FabricObject): string | undefined {
  const withText = object as FabricObject & { text?: string }
  if (typeof withText.text === 'string' && withText.text.length > 0) {
    return withText.text
  }

  if ('getObjects' in object && typeof (object as Group).getObjects === 'function') {
    for (const child of (object as Group).getObjects()) {
      const text = readObjectText(child)
      if (text) {
        return text
      }
    }
  }

  return undefined
}

export function forEachCanvasObject(canvas: Canvas, fn: (object: FabricObject) => void): void {
  canvas.getObjects().forEach(fn)
}

export function clearCanvas(canvas: Canvas): void {
  canvas.clear()
  canvas.backgroundColor = 'transparent'
  canvas.requestRenderAll()
}

export function createCalibrationHandle(x: number, y: number): Circle {
  return new Circle({
    left: x - 5,
    top: y - 5,
    radius: 5,
    fill: '#2563eb',
    stroke: '#fff',
    strokeWidth: 1,
  })
}
