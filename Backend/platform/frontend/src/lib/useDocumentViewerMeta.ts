import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import type { AuditEventItem, DocumentVersionItem } from '../types'

export function useDocumentViewerMeta(projectId: string, documentId: string, version: number) {
  const [versions, setVersions] = useState<DocumentVersionItem[]>([])
  const [auditEvents, setAuditEvents] = useState<AuditEventItem[]>([])
  const [calibration, setCalibration] = useState<{ pixelsPerUnit: number | null; unitLabel: string | null }>({
    pixelsPerUnit: null,
    unitLabel: null,
  })

  const refreshAuditEvents = useCallback(async () => {
    if (!projectId) {
      return
    }
    try {
      const events = await api.listAuditEvents({ projectId })
      setAuditEvents(events)
    } catch {
      setAuditEvents([])
    }
  }, [projectId])

  useEffect(() => {
    let cancelled = false

    async function loadCalibration() {
      try {
        const settings = await api.getCalibration(documentId, version)
        if (!cancelled) {
          setCalibration({
            pixelsPerUnit: settings.pixelsPerUnit,
            unitLabel: settings.unitLabel,
          })
        }
      } catch {
        if (!cancelled) {
          setCalibration({ pixelsPerUnit: null, unitLabel: null })
        }
      }
    }

    void loadCalibration()
    void refreshAuditEvents()

    return () => {
      cancelled = true
    }
  }, [documentId, refreshAuditEvents, version])

  useEffect(() => {
    let cancelled = false

    async function loadVersions() {
      try {
        const items = await api.listDocumentVersions(documentId)
        if (!cancelled) {
          setVersions(items)
        }
      } catch {
        if (!cancelled) {
          setVersions([])
        }
      }
    }

    void loadVersions()

    return () => {
      cancelled = true
    }
  }, [documentId])

  return {
    versions,
    setVersions,
    auditEvents,
    refreshAuditEvents,
    calibration,
    setCalibration,
  }
}
