import { useCallback, useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import type { Canvas, FabricObject, TPointerEventInfo } from 'fabric'
import { api } from '../lib/api'
import { canEditAnnotation, extractAnnotationData } from '../lib/annotations'
import {
  buildFabricObject,
  clearCanvas,
  computeMeasureLabel,
  createFabricCanvas,
  createPreviewShape,
  getAnnotatedObject,
  getAnnotationId,
  makeObjectSelectable,
  setAnnotationId,
  setDrawingTool,
  stampLinePoints,
} from '../lib/annotationFabric'
import { getUser } from '../lib/auth'
import { formatDocumentVersionLabel } from '../lib/format'
import { useI18n } from '../lib/LanguageContext'
import { useDocumentViewerMeta } from '../lib/useDocumentViewerMeta'
import { usePdfDocument } from '../lib/usePdfDocument'
import {
  annotationsDifferFromSaved,
  createLocalAnnotationId,
  isLocalAnnotationId,
  type LocalUndoEntry,
} from '../lib/pdfViewerUtils'
import { AnnotationHistoryPanel } from '../components/AnnotationHistoryPanel'
import { AnnotationToolbar } from '../components/AnnotationToolbar'
import { LanguageSwitcher } from '../components/LanguageSwitcher'
import { PdfViewerLeaveDialog } from '../components/PdfViewerLeaveDialog'
import type {
  AnnotationData,
  AnnotationItem,
  AnnotationRevision,
  AnnotationType,
  ViewerTool,
} from '../types'

const TWO_POINT_TOOLS: AnnotationType[] = ['HIGHLIGHT', 'ARROW', 'MEASURE']
const SINGLE_POINT_TOOLS: AnnotationType[] = ['TEXT', 'STAMP']

export function PdfViewerPage() {
  const { projectId = '', documentId = '' } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const version = Number(searchParams.get('version') ?? '1')
  const currentUser = getUser()
  const { t, locale } = useI18n()
  const {
    pdfDoc,
    pageNumber,
    setPageNumber,
    scale,
    setScale,
    loading,
    error,
    setError,
  } = usePdfDocument(documentId, version, t('viewer.loadPdfFailed'))
  const {
    versions,
    setVersions,
    auditEvents,
    refreshAuditEvents,
    calibration,
    setCalibration,
  } = useDocumentViewerMeta(projectId, documentId, version)

  const pdfCanvasRef = useRef<HTMLCanvasElement>(null)
  const fabricCanvasRef = useRef<HTMLCanvasElement>(null)
  const fabricRef = useRef<Canvas | null>(null)
  const viewerBodyRef = useRef<HTMLDivElement>(null)
  const isPanningRef = useRef(false)
  const spaceHeldRef = useRef(false)
  const panStartRef = useRef({ x: 0, y: 0, scrollLeft: 0, scrollTop: 0 })
  const drawStartRef = useRef<{ x: number; y: number } | null>(null)
  const previewRef = useRef<FabricObject | null>(null)
  const canvasSizeRef = useRef({ width: 0, height: 0 })
  const suppressModifyRef = useRef(false)
  const undoStackRef = useRef<LocalUndoEntry[]>([])
  const applyingHistoryRef = useRef(false)
  const pdfRenderTaskRef = useRef<{ cancel: () => void } | null>(null)
  const lastSavedRef = useRef<AnnotationItem[]>([])
  const annotationsRef = useRef<AnnotationItem[]>([])
  const selectedAnnotationIdRef = useRef<string | null>(null)
  const isDirtyRef = useRef(false)

  const [isPanning, setIsPanning] = useState(false)
  const [activeTool, setActiveTool] = useState<ViewerTool>('select')
  const activeToolRef = useRef(activeTool)
  activeToolRef.current = activeTool
  const [annotations, setAnnotations] = useState<AnnotationItem[]>([])
  const [selectedAnnotationId, setSelectedAnnotationId] = useState<string | null>(null)
  const [revisions, setRevisions] = useState<AnnotationRevision[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [statusMessage, setStatusMessage] = useState<string | null>(null)
  const [fabricGeneration, setFabricGeneration] = useState(0)
  const [undoCount, setUndoCount] = useState(0)
  const [isDirty, setIsDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [annotationsReady, setAnnotationsReady] = useState(false)
  const [leaveOpen, setLeaveOpen] = useState(false)
  const pendingLeaveRef = useRef<string | null>(null)

  const refreshAnnotations = useCallback(async () => {
    const items = await api.listAnnotations(documentId, version)
    setAnnotations(items)
    return items
  }, [documentId, version])

  const loadRevisions = useCallback(async (annotationId: string) => {
    setHistoryLoading(true)
    try {
      const items = await api.listAnnotationRevisions(annotationId)
      setRevisions(items)
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  const recordUndoPoint = useCallback((entry: LocalUndoEntry) => {
    if (applyingHistoryRef.current) {
      return
    }

    undoStackRef.current = [...undoStackRef.current, entry]
    setUndoCount(undoStackRef.current.length)
  }, [])

  function selectCanvasObject(object: FabricObject, annotationId: string) {
    const canvas = fabricRef.current
    setSelectedAnnotationId(annotationId)
    if (!canvas) {
      return
    }
    makeObjectSelectable(object)
    if (activeToolRef.current === 'select') {
      canvas.setActiveObject(object)
    } else {
      canvas.discardActiveObject()
    }
    canvas.requestRenderAll()
  }

  function addLocalAnnotation(type: AnnotationType, object: FabricObject, extra: AnnotationData = {}) {
    if (!currentUser) {
      setStatusMessage(t('viewer.signInMarkup'))
      return
    }

    const { width, height } = canvasSizeRef.current
    const data = extractAnnotationData(type, object, width, height, extra)
    const item: AnnotationItem = {
      id: createLocalAnnotationId(),
      documentVersionId: lastSavedRef.current[0]?.documentVersionId ?? '',
      documentId,
      pageNumber,
      type,
      data,
      createdById: currentUser.id,
      createdByName: currentUser.fullName,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      deleted: false,
    }

    makeObjectSelectable(object)
    setAnnotationId(object, item.id)
    if (type === 'ARROW' || type === 'MEASURE') {
      stampLinePoints(
        object,
        Number(data.x1) * width,
        Number(data.y1) * height,
        Number(data.x2) * width,
        Number(data.y2) * height,
      )
    }

    recordUndoPoint({ kind: 'create', ids: [item.id] })
    setAnnotations((current) => [...current, item])
    selectCanvasObject(object, item.id)
    setRevisions([])
  }

  const renderAnnotationsOnCanvas = useCallback(
    async (items: AnnotationItem[]) => {
      const canvas = fabricRef.current
      const { width, height } = canvasSizeRef.current
      if (!canvas || width === 0 || height === 0) {
        return
      }

      suppressModifyRef.current = true
      clearCanvas(canvas)

      for (const annotation of items) {
        const object = buildFabricObject(annotation, width, height)
        canvas.add(object)
      }

      canvas.requestRenderAll()
      suppressModifyRef.current = false
    },
    [],
  )

  useEffect(() => {
    undoStackRef.current = []
    setUndoCount(0)
    setIsDirty(false)
    lastSavedRef.current = []
    setAnnotations([])
    setAnnotationsReady(false)
  }, [documentId, version])

  useEffect(() => {
    let cancelled = false

    async function loadAllAnnotations() {
      try {
        const items = await api.listAnnotations(documentId, version)
        if (!cancelled) {
          lastSavedRef.current = items
          setAnnotations(items)
          setIsDirty(false)
          setAnnotationsReady(true)
        }
      } catch {
        if (!cancelled) {
          lastSavedRef.current = []
          setAnnotations([])
          setAnnotationsReady(true)
        }
      }
    }

    void loadAllAnnotations()

    return () => {
      cancelled = true
    }
  }, [documentId, version])

  useEffect(() => {
    annotationsRef.current = annotations
  }, [annotations])

  useEffect(() => {
    selectedAnnotationIdRef.current = selectedAnnotationId
  }, [selectedAnnotationId])

  useEffect(() => {
    isDirtyRef.current = annotationsDifferFromSaved(annotations, lastSavedRef.current)
    setIsDirty(isDirtyRef.current)
  }, [annotations])

  useEffect(() => {
    const canvas = fabricRef.current
    if (!canvas || !annotationsReady) {
      return
    }

    void renderAnnotationsOnCanvas(
      annotationsRef.current.filter((item) => item.pageNumber === pageNumber),
    ).then(() => {
      const selectedId = selectedAnnotationIdRef.current
      if (!selectedId || !fabricRef.current) {
        return
      }
      const target = fabricRef.current.getObjects().find((object) => getAnnotationId(object) === selectedId)
      if (target && activeToolRef.current === 'select') {
        fabricRef.current.setActiveObject(target)
        fabricRef.current.requestRenderAll()
      }
    })
  }, [annotationsReady, fabricGeneration, pageNumber, renderAnnotationsOnCanvas])

  useEffect(() => {
    const doc = pdfDoc
    const pdfCanvas = pdfCanvasRef.current
    const fabricCanvasElement = fabricCanvasRef.current
    if (!doc || !pdfCanvas || !fabricCanvasElement) {
      return
    }

    let cancelled = false

    async function renderPage() {
      if (!doc) {
        return
      }

      pdfRenderTaskRef.current?.cancel()
      pdfRenderTaskRef.current = null

      const page = await doc.getPage(pageNumber)
      if (cancelled) {
        return
      }

      const viewport = page.getViewport({ scale })
      pdfCanvas!.width = viewport.width
      pdfCanvas!.height = viewport.height
      canvasSizeRef.current = { width: viewport.width, height: viewport.height }

      const context = pdfCanvas!.getContext('2d')
      if (!context) {
        return
      }

      if (fabricRef.current) {
        fabricRef.current.dispose()
        fabricRef.current = null
      }

      const fabricCanvas = createFabricCanvas(fabricCanvasElement!, viewport.width, viewport.height)
      fabricRef.current = fabricCanvas
      setDrawingTool(fabricCanvas, activeTool)
      setFabricGeneration((generation) => generation + 1)

      const renderTask = page.render({
        canvas: pdfCanvas,
        canvasContext: context,
        viewport,
      })
      pdfRenderTaskRef.current = renderTask

      try {
        await renderTask.promise
      } catch {
        if (cancelled) {
          return
        }
      }

      if (cancelled) {
        return
      }

      try {
        const items = annotationsRef.current.filter((item) => item.pageNumber === pageNumber)
        if (!cancelled) {
          await renderAnnotationsOnCanvas(items)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : t('viewer.loadAnnotationsFailed'))
        }
      }
    }

    void renderPage()

    return () => {
      cancelled = true
      pdfRenderTaskRef.current?.cancel()
      pdfRenderTaskRef.current = null
      fabricRef.current?.dispose()
      fabricRef.current = null
    }
  }, [pageNumber, pdfDoc, renderAnnotationsOnCanvas, scale])

  useEffect(() => {
    const canvas = fabricRef.current
    if (!canvas) {
      return
    }
    setDrawingTool(canvas, activeTool)
  }, [activeTool, fabricGeneration])

  useEffect(() => {
    const canvas = fabricRef.current
    if (!canvas) {
      return
    }

    function persistModification(event: { target?: FabricObject }) {
      if (suppressModifyRef.current || !event.target) {
        return
      }

      const object = getAnnotatedObject(event.target) ?? event.target
      const annotationId = getAnnotationId(object)
      if (!annotationId) {
        return
      }

      const annotation = annotations.find((item) => item.id === annotationId)
      if (!annotation || !currentUser || !canEditAnnotation(annotation, currentUser.id, currentUser.role)) {
        return
      }

      const { width, height } = canvasSizeRef.current
      const data = extractAnnotationData(annotation.type, object, width, height, annotation.data)
      if (annotation.type === 'ARROW' || annotation.type === 'MEASURE') {
        stampLinePoints(
          object,
          Number(data.x1) * width,
          Number(data.y1) * height,
          Number(data.x2) * width,
          Number(data.y2) * height,
        )
      }

      recordUndoPoint({ kind: 'update', id: annotationId, before: annotation.data })
      setAnnotations((current) =>
        current.map((item) => (item.id === annotationId ? { ...item, data, updatedAt: new Date().toISOString() } : item)),
      )
      selectCanvasObject(object, annotationId)
      if (!isLocalAnnotationId(annotationId)) {
        void loadRevisions(annotationId)
      }
    }

    function persistNewPath(event: { path?: FabricObject }) {
      const canvasInstance = fabricRef.current
      if (suppressModifyRef.current || !event.path || activeTool !== 'PEN' || !canvasInstance) {
        return
      }

      const object = event.path
      makeObjectSelectable(object)
      addLocalAnnotation('PEN', object)
    }

    function handleSelection(event: { selected?: FabricObject[] }) {
      const selected = event.selected?.[0]
      const annotated = selected ? getAnnotatedObject(selected) ?? selected : null
      const annotationId = annotated ? getAnnotationId(annotated) : undefined
      if (annotated && annotationId && annotated !== selected) {
        const canvasInstance = fabricRef.current
        canvasInstance?.setActiveObject(annotated)
      }
      setSelectedAnnotationId(annotationId ?? null)
      if (annotationId && !isLocalAnnotationId(annotationId)) {
        void loadRevisions(annotationId)
      } else {
        setRevisions([])
      }
    }

    canvas.on('object:modified', persistModification)
    canvas.on('path:created', persistNewPath)
    canvas.on('selection:created', handleSelection)
    canvas.on('selection:updated', handleSelection)
    canvas.on('selection:cleared', () => {
      queueMicrotask(() => {
        if (fabricRef.current?.getActiveObject()) {
          return
        }
        setSelectedAnnotationId(null)
        setRevisions([])
      })
    })

    return () => {
      canvas.off('object:modified', persistModification)
      canvas.off('path:created', persistNewPath)
      canvas.off('selection:created', handleSelection)
      canvas.off('selection:updated', handleSelection)
      canvas.off('selection:cleared')
    }
  }, [
    activeTool,
    annotations,
    currentUser,
    documentId,
    loadRevisions,
    pageNumber,
    recordUndoPoint,
    version,
    fabricGeneration,
  ])

  useEffect(() => {
    function handlePanMove(event: PointerEvent) {
      if (!isPanningRef.current) {
        return
      }

      const viewerBody = viewerBodyRef.current
      if (!viewerBody) {
        return
      }

      const deltaX = event.clientX - panStartRef.current.x
      const deltaY = event.clientY - panStartRef.current.y
      viewerBody.scrollLeft = panStartRef.current.scrollLeft - deltaX
      viewerBody.scrollTop = panStartRef.current.scrollTop - deltaY
    }

    function handlePanEnd() {
      if (!isPanningRef.current) {
        return
      }
      isPanningRef.current = false
      setIsPanning(false)
      const canvas = fabricRef.current
      if (canvas) {
        setDrawingTool(canvas, activeToolRef.current)
      }
    }

    window.addEventListener('pointermove', handlePanMove)
    window.addEventListener('pointerup', handlePanEnd)
    window.addEventListener('pointercancel', handlePanEnd)

    return () => {
      window.removeEventListener('pointermove', handlePanMove)
      window.removeEventListener('pointerup', handlePanEnd)
      window.removeEventListener('pointercancel', handlePanEnd)
    }
  }, [])

  function getCanvasPoint(event: TPointerEventInfo): { x: number; y: number } | null {
    const canvas = fabricRef.current
    if (!canvas) {
      return null
    }
    return canvas.getScenePoint(event.e)
  }

  function saveAnnotation(type: AnnotationType, object: FabricObject, extra: Record<string, string | number> = {}) {
    addLocalAnnotation(type, object, extra)
  }

  function getPointerClient(event: TPointerEventInfo): { x: number; y: number; button: number } | null {
    const native = event.e
    if ('clientX' in native && typeof native.clientX === 'number') {
      return {
        x: native.clientX,
        y: native.clientY,
        button: 'button' in native && typeof native.button === 'number' ? native.button : 0,
      }
    }
    if ('touches' in native && native.touches[0]) {
      return { x: native.touches[0].clientX, y: native.touches[0].clientY, button: 0 }
    }
    return null
  }

  function beginPan(clientX: number, clientY: number) {
    const viewerBody = viewerBodyRef.current
    if (!viewerBody || loading || error) {
      return false
    }

    isPanningRef.current = true
    setIsPanning(true)
    panStartRef.current = {
      x: clientX,
      y: clientY,
      scrollLeft: viewerBody.scrollLeft,
      scrollTop: viewerBody.scrollTop,
    }

    const canvas = fabricRef.current
    if (canvas) {
      canvas.selection = false
      canvas.skipTargetFind = true
      canvas.discardActiveObject()
      canvas.requestRenderAll()
    }

    return true
  }

  function handlePanStart(event: ReactMouseEvent<HTMLDivElement>) {
    if (loading || error) {
      return
    }

    const panWithModifier = event.button === 1 || spaceHeldRef.current
    if (!panWithModifier && activeTool !== 'pan') {
      return
    }

    if (beginPan(event.clientX, event.clientY)) {
      event.preventDefault()
    }
  }

  async function handleFabricMouseDown(event: TPointerEventInfo) {
    const pointer = getPointerClient(event)
    if (!pointer) {
      return
    }

    const panWithSelect = activeTool === 'select' && !event.target
    const panWithTool = activeTool === 'pan'
    const panWithModifier = spaceHeldRef.current || pointer.button === 1
    if (panWithModifier || panWithTool || panWithSelect) {
      if (beginPan(pointer.x, pointer.y) && 'preventDefault' in event.e) {
        event.e.preventDefault()
      }
      return
    }

    if (!fabricRef.current || activeTool === 'select' || activeTool === 'PEN') {
      return
    }

    const point = getCanvasPoint(event)
    if (!point) {
      return
    }

    if (SINGLE_POINT_TOOLS.includes(activeTool)) {
      const shape = createPreviewShape(activeTool, point.x, point.y, point.x, point.y)
      if (!shape) {
        return
      }

      if (activeTool === 'STAMP') {
        shape.set({ left: point.x, top: point.y })
      }

      fabricRef.current.add(shape)
      saveAnnotation(activeTool, shape, {
        text: activeTool === 'STAMP' ? 'APPROVED' : 'Note',
      })
      return
    }

    if (TWO_POINT_TOOLS.includes(activeTool)) {
      drawStartRef.current = { x: point.x, y: point.y }
      previewRef.current = createPreviewShape(activeTool, point.x, point.y, point.x, point.y)
      if (previewRef.current) {
        fabricRef.current.add(previewRef.current)
      }
    }
  }

  function handleFabricMouseMove(event: TPointerEventInfo) {
    const canvas = fabricRef.current
    const start = drawStartRef.current
    const preview = previewRef.current
    if (!canvas || !start || !preview || !TWO_POINT_TOOLS.includes(activeTool as AnnotationType)) {
      return
    }

    const point = getCanvasPoint(event)
    if (!point) {
      return
    }

    canvas.remove(preview)

    if (activeTool === 'HIGHLIGHT') {
      previewRef.current = createPreviewShape('HIGHLIGHT', start.x, start.y, point.x, point.y)
    } else if (activeTool === 'ARROW') {
      previewRef.current = createPreviewShape('ARROW', start.x, start.y, point.x, point.y)
    } else {
      const label = computeMeasureLabel(
        start.x,
        start.y,
        point.x,
        point.y,
        calibration.pixelsPerUnit,
        calibration.unitLabel,
      )
      previewRef.current = createPreviewShape('MEASURE', start.x, start.y, point.x, point.y)
      if (previewRef.current && 'set' in previewRef.current) {
        previewRef.current.set({ data: { label } })
      }
    }

    if (previewRef.current) {
      canvas.add(previewRef.current)
      canvas.requestRenderAll()
    }
  }

  async function handleFabricMouseUp(event: TPointerEventInfo) {
    const canvas = fabricRef.current
    const start = drawStartRef.current
    let preview = previewRef.current
    if (!canvas || !start || !preview || !TWO_POINT_TOOLS.includes(activeTool as AnnotationType)) {
      return
    }

    const point = getCanvasPoint(event)
    if (!point) {
      return
    }

    canvas.remove(preview)
    previewRef.current = null
    drawStartRef.current = null

    if (Math.hypot(point.x - start.x, point.y - start.y) < 4) {
      return
    }

    preview = createPreviewShape(activeTool as AnnotationType, start.x, start.y, point.x, point.y)
    if (!preview) {
      return
    }

    canvas.add(preview)

    const extra: Record<string, string | number> =
      activeTool === 'MEASURE'
        ? {
            label: computeMeasureLabel(
              start.x,
              start.y,
              point.x,
              point.y,
              calibration.pixelsPerUnit,
              calibration.unitLabel,
            ),
          }
        : activeTool === 'ARROW'
          ? { x1: start.x, y1: start.y, x2: point.x, y2: point.y }
          : {}
    saveAnnotation(activeTool as AnnotationType, preview, extra)
  }

  useEffect(() => {
    const canvas = fabricRef.current
    if (!canvas) {
      return
    }

    const onMouseDown = (event: TPointerEventInfo) => {
      void handleFabricMouseDown(event)
    }
    const onMouseMove = (event: TPointerEventInfo) => {
      handleFabricMouseMove(event)
    }
    const onMouseUp = (event: TPointerEventInfo) => {
      void handleFabricMouseUp(event)
    }

    canvas.on('mouse:down', onMouseDown)
    canvas.on('mouse:move', onMouseMove)
    canvas.on('mouse:up', onMouseUp)

    return () => {
      canvas.off('mouse:down', onMouseDown)
      canvas.off('mouse:move', onMouseMove)
      canvas.off('mouse:up', onMouseUp)
    }
  }, [activeTool, calibration.pixelsPerUnit, calibration.unitLabel, documentId, fabricGeneration, pageNumber, version])

  function handleDeleteSelected() {
    const canvas = fabricRef.current
    const selectedObjects = canvas?.getActiveObjects() ?? []
    const annotatedObjects = selectedObjects
      .map((object) => getAnnotatedObject(object) ?? object)
      .filter((object, index, all) => all.indexOf(object) === index)

    const ids = new Set<string>()
    for (const object of annotatedObjects) {
      const id = getAnnotationId(object)
      if (id) {
        ids.add(id)
      }
    }
    if (selectedAnnotationId) {
      ids.add(selectedAnnotationId)
    }

    if (ids.size === 0 || !currentUser) {
      return
    }

    const items = annotations.filter((item) => ids.has(item.id) && canEditAnnotation(item, currentUser.id, currentUser.role))
    if (items.length === 0) {
      return
    }

    const removableIds = new Set(items.map((item) => item.id))
    recordUndoPoint({ kind: 'delete', items })
    setAnnotations((current) => current.filter((item) => !removableIds.has(item.id)))
    setSelectedAnnotationId(null)
    setRevisions([])

    if (canvas) {
      for (const object of canvas.getObjects()) {
        const id = getAnnotationId(object)
        if (id && removableIds.has(id)) {
          canvas.remove(object)
        }
      }
      canvas.discardActiveObject()
      canvas.requestRenderAll()
    }
  }

  async function handleRollback(annotationId: string) {
    if (isDirty) {
      setStatusMessage(t('viewer.saveBeforeRollback'))
      return
    }

    if (isLocalAnnotationId(annotationId)) {
      return
    }

    const annotation = annotations.find((item) => item.id === annotationId)
    if (!annotation || !currentUser || !canEditAnnotation(annotation, currentUser.id, currentUser.role)) {
      return
    }

    const updated = await api.rollbackAnnotation(annotationId)
    if (updated.deleted) {
      setAnnotations((current) => current.filter((item) => item.id !== updated.id))
      setSelectedAnnotationId(null)
      setRevisions([])
    } else {
      setAnnotations((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      await loadRevisions(updated.id)
    }

    const items = await refreshAnnotations()
    lastSavedRef.current = items
    await renderAnnotationsOnCanvas(items.filter((item) => item.pageNumber === pageNumber))
    await refreshAuditEvents()
  }

  const redrawCurrentPage = useCallback(async (items: AnnotationItem[], selectedId: string | null = null) => {
    await renderAnnotationsOnCanvas(items.filter((item) => item.pageNumber === pageNumber))
    const canvas = fabricRef.current
    if (!canvas || !selectedId) {
      return
    }
    const target = canvas.getObjects().find((object) => getAnnotationId(object) === selectedId)
    if (target && activeToolRef.current === 'select') {
      canvas.setActiveObject(target)
      canvas.requestRenderAll()
    }
  }, [pageNumber, renderAnnotationsOnCanvas])

  const handleUndo = useCallback(() => {
    const stack = undoStackRef.current
    if (stack.length === 0) {
      return
    }

    const entry = stack[stack.length - 1]
    undoStackRef.current = stack.slice(0, -1)
    setUndoCount(undoStackRef.current.length)
    applyingHistoryRef.current = true

    try {
      if (entry.kind === 'create') {
        const ids = new Set(entry.ids)
        setAnnotations((current) => current.filter((item) => !ids.has(item.id)))
        const canvas = fabricRef.current
        if (canvas) {
          for (const object of canvas.getObjects()) {
            const id = getAnnotationId(object)
            if (id && ids.has(id)) {
              canvas.remove(object)
            }
          }
          canvas.discardActiveObject()
          canvas.requestRenderAll()
        }
        setSelectedAnnotationId(null)
        setRevisions([])
      } else if (entry.kind === 'update') {
        setAnnotations((current) => {
          const next = current.map((item) =>
            item.id === entry.id ? { ...item, data: entry.before, updatedAt: new Date().toISOString() } : item,
          )
          void redrawCurrentPage(next, entry.id)
          return next
        })
        setSelectedAnnotationId(entry.id)
      } else {
        setAnnotations((current) => {
          const next = [...current, ...entry.items]
          void redrawCurrentPage(next, entry.items[0]?.id ?? null)
          return next
        })
        setSelectedAnnotationId(entry.items[0]?.id ?? null)
      }

      setStatusMessage(t('viewer.undid'))
    } finally {
      applyingHistoryRef.current = false
    }
  }, [redrawCurrentPage])

  async function handleSave(options?: { openCreatedVersion?: boolean }) {
    if (saving) {
      return false
    }

    if (!isDirtyRef.current) {
      setStatusMessage(t('viewer.nothingToSave'))
      return true
    }

    setSaving(true)
    try {
      const current = annotationsRef.current
      const created = await api.saveMarkupVersion(documentId, version, {
        annotations: current.map((item) => ({
          pageNumber: item.pageNumber,
          type: item.type,
          data: item.data,
          sourceAnnotationId: isLocalAnnotationId(item.id) ? undefined : item.id,
        })),
      })

      isDirtyRef.current = false
      setIsDirty(false)
      lastSavedRef.current = current
      undoStackRef.current = []
      setUndoCount(0)

      try {
        setVersions(await api.listDocumentVersions(documentId))
      } catch {
        setVersions((items) => [created, ...items.filter((item) => item.id !== created.id)])
      }

      await refreshAuditEvents()
      setStatusMessage(
        created.uploadedByName
          ? t('viewer.savedAsBy', { version: created.versionNumber, name: created.uploadedByName })
          : t('viewer.savedAs', { version: created.versionNumber }),
      )

      if (options?.openCreatedVersion !== false) {
        setSearchParams({ version: String(created.versionNumber) })
      }

      return true
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : t('viewer.saveFailed'))
      return false
    } finally {
      setSaving(false)
    }
  }

  async function discardChanges() {
    const saved = lastSavedRef.current
    undoStackRef.current = []
    setUndoCount(0)
    setAnnotations(saved)
    setSelectedAnnotationId(null)
    setRevisions([])
    await renderAnnotationsOnCanvas(saved.filter((item) => item.pageNumber === pageNumber))
  }

  useEffect(() => {
    function isTypingTarget(target: EventTarget | null) {
      if (!(target instanceof HTMLElement)) {
        return false
      }
      const tag = target.tagName
      return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
    }

    function handleKeyDown(event: KeyboardEvent) {
      const canvas = fabricRef.current
      const active = canvas?.getActiveObject() as (FabricObject & { isEditing?: boolean }) | undefined
      if (active?.isEditing || isTypingTarget(event.target)) {
        return
      }

      const isUndo = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z' && !event.shiftKey
      if (isUndo) {
        event.preventDefault()
        handleUndo()
        return
      }

      const isSave = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's'
      if (isSave) {
        event.preventDefault()
        void handleSave()
        return
      }

      if (event.code === 'Space') {
        spaceHeldRef.current = true
        event.preventDefault()
        return
      }

      if (event.key === 'Delete' || event.key === 'Backspace') {
        event.preventDefault()
        handleDeleteSelected()
      }
    }

    function handleKeyUp(event: KeyboardEvent) {
      if (event.code === 'Space') {
        spaceHeldRef.current = false
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('keyup', handleKeyUp)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('keyup', handleKeyUp)
    }
  }, [annotations, currentUser, handleUndo, selectedAnnotationId])

  useEffect(() => {
    function onBeforeUnload(event: BeforeUnloadEvent) {
      if (!isDirtyRef.current) {
        return
      }
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [])

  function requestLeave(href: string) {
    if (!isDirtyRef.current) {
      navigate(href)
      return
    }
    pendingLeaveRef.current = href
    setLeaveOpen(true)
  }

  async function confirmSaveAndLeave() {
    const savedOk = await handleSave({ openCreatedVersion: false })
    if (!savedOk) {
      return
    }
    const href = pendingLeaveRef.current
    pendingLeaveRef.current = null
    setLeaveOpen(false)
    if (href) {
      navigate(href)
    }
  }

  async function confirmDiscardAndLeave() {
    await discardChanges()
    const href = pendingLeaveRef.current
    pendingLeaveRef.current = null
    setLeaveOpen(false)
    if (href) {
      navigate(href)
    }
  }

  async function handleOpenCalibration() {
    if (!currentUser || currentUser.role === 'SITE_WORKER') {
      setStatusMessage(t('viewer.calibrationForbidden'))
      return
    }

    const pixelsPerUnit = window.prompt(t('viewer.calibrationPixels'), String(calibration.pixelsPerUnit ?? '10'))
    if (!pixelsPerUnit) {
      return
    }

    const unitLabel = window.prompt(t('viewer.calibrationUnit'), calibration.unitLabel ?? 'm')
    if (!unitLabel) {
      return
    }

    const parsed = Number(pixelsPerUnit)
    if (!Number.isFinite(parsed) || parsed <= 0) {
      setStatusMessage(t('viewer.calibrationInvalid'))
      return
    }

    const saved = await api.updateCalibration(documentId, version, parsed, unitLabel)
    setCalibration({
      pixelsPerUnit: saved.pixelsPerUnit,
      unitLabel: saved.unitLabel,
    })
    setStatusMessage(
      t('viewer.scaleSet', {
        pixels: saved.pixelsPerUnit ?? parsed,
        unit: saved.unitLabel ?? unitLabel ?? 'm',
      }),
    )
  }

  function handleSelectAnnotation(annotationId: string) {
    setActiveTool('select')
    setSelectedAnnotationId(annotationId)
    if (!isLocalAnnotationId(annotationId)) {
      void loadRevisions(annotationId)
    } else {
      setRevisions([])
    }

    const canvas = fabricRef.current
    if (!canvas) {
      return
    }

    const target = canvas.getObjects().find((object) => getAnnotationId(object) === annotationId)
    if (target) {
      setDrawingTool(canvas, 'select')
      canvas.setActiveObject(target)
      canvas.requestRenderAll()
    }
  }

  const selectedAnnotation = annotations.find((item) => item.id === selectedAnnotationId) ?? null
  const canEditSelected =
    !!selectedAnnotation &&
    !!currentUser &&
    canEditAnnotation(selectedAnnotation, currentUser.id, currentUser.role)

  function openDocumentVersion(nextVersion: number) {
    if (nextVersion === version) {
      return
    }

    const href = `/projects/${projectId}/documents/${documentId}?version=${nextVersion}`
    if (isDirtyRef.current) {
      requestLeave(href)
      return
    }

    setSearchParams({ version: String(nextVersion) })
  }

  const totalPages = pdfDoc?.numPages ?? 0
  const zoomPercent = Math.round(scale * 100)
  const calibrationLabel =
    calibration.pixelsPerUnit && calibration.unitLabel
      ? `${calibration.pixelsPerUnit}px/${calibration.unitLabel}`
      : null

  return (
    <div className="viewer-layout viewer-layout-annotated">
      <header className="viewer-header">
        <div className="viewer-header-left">
          <Link
            to={`/projects/${projectId}`}
            className="viewer-back"
            onClick={(event) => {
              if (!isDirty) {
                return
              }
              event.preventDefault()
              requestLeave(`/projects/${projectId}`)
            }}
          >
            {t('viewer.back')}
          </Link>
          <span className="viewer-title">{t('viewer.title')}</span>
          {isDirty && <span className="viewer-unsaved">{t('viewer.unsaved')}</span>}
          {versions.length > 0 && (
            <label className="viewer-version">
              <span>{t('viewer.version')}</span>
              <select
                value={version}
                onChange={(event) => openDocumentVersion(Number(event.target.value))}
              >
                {versions.map((item) => (
                  <option key={item.versionNumber} value={item.versionNumber}>
                    {formatDocumentVersionLabel(item, locale)}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>

        <div className="viewer-controls">
          <LanguageSwitcher variant="viewer" />
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={pageNumber <= 1}
            onClick={() => setPageNumber((page) => page - 1)}
          >
            {t('viewer.previous')}
          </button>
          <span className="viewer-page-indicator">
            {t('viewer.page', { current: pageNumber, total: totalPages || '—' })}
          </span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={!totalPages || pageNumber >= totalPages}
            onClick={() => setPageNumber((page) => page + 1)}
          >
            {t('viewer.next')}
          </button>

          <span className="viewer-divider" aria-hidden="true" />

          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => setScale((s) => Math.max(0.5, s - 0.25))}
          >
            −
          </button>
          <span className="viewer-zoom">{zoomPercent}%</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setScale((s) => s + 0.25)}>
            +
          </button>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setScale(1.25)}>
            {t('viewer.reset')}
          </button>
        </div>
      </header>

      <AnnotationToolbar
        activeTool={activeTool}
        onToolChange={setActiveTool}
        onDeleteSelected={() => handleDeleteSelected()}
        onUndo={() => handleUndo()}
        onSave={() => void handleSave()}
        canUndo={undoCount > 0}
        canSave={isDirty}
        saving={saving}
        hasSelection={!!selectedAnnotationId}
        canEditSelection={canEditSelected}
        calibrationLabel={calibrationLabel}
        onOpenCalibration={() => void handleOpenCalibration()}
      />

      {statusMessage && (
        <div className="viewer-status">
          <span>{statusMessage}</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setStatusMessage(null)}>
            {t('viewer.dismiss')}
          </button>
        </div>
      )}

      <div className="viewer-main">
        <div
          ref={viewerBodyRef}
          className={`viewer-body${isPanning ? ' is-panning' : ''}${activeTool === 'pan' ? ' tool-pan' : ''}${activeTool === 'select' ? ' tool-select' : ''}`}
          onMouseDown={handlePanStart}
          onAuxClick={(event) => event.preventDefault()}
        >
          {loading && (
            <div className="viewer-state">
              <span className="spinner spinner-light" aria-hidden="true" />
              {t('viewer.loading')}
            </div>
          )}
          {error && <div className="alert alert-error viewer-state">{error}</div>}
          {!loading && !error && (
            <div className="viewer-canvas-wrap">
              <canvas ref={pdfCanvasRef} className="pdf-canvas" draggable={false} />
              <div className="fabric-layer">
                <canvas ref={fabricCanvasRef} className="fabric-canvas" draggable={false} />
              </div>
            </div>
          )}
        </div>

        <AnnotationHistoryPanel
          annotations={annotations.filter((item) => item.pageNumber === pageNumber)}
          selectedAnnotationId={selectedAnnotationId}
          revisions={revisions}
          auditEvents={auditEvents}
          versions={versions}
          currentVersion={version}
          onSelectAnnotation={handleSelectAnnotation}
          onOpenVersion={openDocumentVersion}
          onRollback={(annotationId) => void handleRollback(annotationId)}
          canEditSelected={canEditSelected}
          loading={historyLoading}
        />
      </div>

      <PdfViewerLeaveDialog
        open={leaveOpen}
        saving={saving}
        title={t('viewer.leaveTitle')}
        body={t('viewer.leaveBody')}
        saveLabel={t('common.save')}
        savingLabel={t('common.saving')}
        discardLabel={t('viewer.dontSave')}
        stayLabel={t('viewer.stay')}
        onSave={() => void confirmSaveAndLeave()}
        onDiscard={() => void confirmDiscardAndLeave()}
        onStay={() => {
          pendingLeaveRef.current = null
          setLeaveOpen(false)
        }}
      />
    </div>
  )
}
