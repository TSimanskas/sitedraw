type PdfViewerLeaveDialogProps = {
  open: boolean
  saving: boolean
  title: string
  body: string
  saveLabel: string
  savingLabel: string
  discardLabel: string
  stayLabel: string
  onSave: () => void
  onDiscard: () => void
  onStay: () => void
}

export function PdfViewerLeaveDialog({
  open,
  saving,
  title,
  body,
  saveLabel,
  savingLabel,
  discardLabel,
  stayLabel,
  onSave,
  onDiscard,
  onStay,
}: PdfViewerLeaveDialogProps) {
  if (!open) {
    return null
  }

  return (
    <div className="confirm-backdrop" role="presentation">
      <div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="leave-drawing-title">
        <h2 id="leave-drawing-title">{title}</h2>
        <p>{body}</p>
        <div className="confirm-actions">
          <button type="button" className="btn btn-primary" disabled={saving} onClick={onSave}>
            {saving ? savingLabel : saveLabel}
          </button>
          <button type="button" className="btn btn-secondary" disabled={saving} onClick={onDiscard}>
            {discardLabel}
          </button>
          <button type="button" className="btn btn-ghost confirm-stay" onClick={onStay}>
            {stayLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
