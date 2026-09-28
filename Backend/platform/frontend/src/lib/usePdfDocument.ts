import { useEffect, useState } from 'react'
import * as pdfjs from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { api } from './api'
import { getToken } from './auth'

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorker

export function usePdfDocument(documentId: string, version: number, loadFailedMessage: string) {
  const [pdfDoc, setPdfDoc] = useState<pdfjs.PDFDocumentProxy | null>(null)
  const [pageNumber, setPageNumber] = useState(1)
  const [scale, setScale] = useState(1.25)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadPdf() {
      setLoading(true)
      setError(null)

      try {
        const token = getToken()
        const response = await fetch(api.documentFileUrl(documentId, version), {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })

        if (!response.ok) {
          throw new Error(loadFailedMessage)
        }

        const data = await response.arrayBuffer()
        const pdf = await pdfjs.getDocument({ data }).promise

        if (!cancelled) {
          setPdfDoc(pdf)
          setPageNumber(1)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : loadFailedMessage)
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadPdf()

    return () => {
      cancelled = true
      setPdfDoc(null)
    }
  }, [documentId, loadFailedMessage, version])

  return {
    pdfDoc,
    pageNumber,
    setPageNumber,
    scale,
    setScale,
    loading,
    error,
    setError,
  }
}
