type PasswordChangedDialogProps = {
  open: boolean
  title: string
  body: string
  actionLabel: string
  onGoToLogin: () => void
}

export function PasswordChangedDialog({
  open,
  title,
  body,
  actionLabel,
  onGoToLogin,
}: PasswordChangedDialogProps) {
  if (!open) {
    return null
  }

  return (
    <div className="confirm-backdrop" role="presentation">
      <div className="password-changed-dialog" role="dialog" aria-modal="true" aria-labelledby="password-changed-title">
        <h2 id="password-changed-title">{title}</h2>
        <p>{body}</p>
        <button type="button" className="btn btn-primary btn-block" onClick={onGoToLogin}>
          {actionLabel}
        </button>
      </div>
    </div>
  )
}
