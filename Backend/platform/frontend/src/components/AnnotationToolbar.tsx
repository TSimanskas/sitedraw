import { useI18n } from '../lib/LanguageContext'
import type { ViewerTool } from '../types'
import type { MessageKey } from '../lib/i18n'

const TOOLS: Array<{ id: ViewerTool; labelKey: MessageKey }> = [
  { id: 'select', labelKey: 'tool.select' },
  { id: 'pan', labelKey: 'tool.pan' },
  { id: 'HIGHLIGHT', labelKey: 'tool.highlight' },
  { id: 'ARROW', labelKey: 'tool.arrow' },
  { id: 'TEXT', labelKey: 'tool.text' },
  { id: 'STAMP', labelKey: 'tool.stamp' },
  { id: 'MEASURE', labelKey: 'tool.measure' },
  { id: 'PEN', labelKey: 'tool.pen' },
]

interface AnnotationToolbarProps {
  activeTool: ViewerTool
  onToolChange: (tool: ViewerTool) => void
  onDeleteSelected: () => void
  onUndo: () => void
  onSave: () => void
  canUndo: boolean
  canSave: boolean
  saving: boolean
  hasSelection: boolean
  canEditSelection: boolean
  calibrationLabel: string | null
  onOpenCalibration: () => void
}

export function AnnotationToolbar({
  activeTool,
  onToolChange,
  onDeleteSelected,
  onUndo,
  onSave,
  canUndo,
  canSave,
  saving,
  hasSelection,
  canEditSelection,
  calibrationLabel,
  onOpenCalibration,
}: AnnotationToolbarProps) {
  const { t } = useI18n()

  return (
    <div className="viewer-toolbar">
      <div className="viewer-toolbar-group">
        {TOOLS.map((tool) => (
          <button
            key={tool.id}
            type="button"
            className={`btn btn-ghost btn-sm${activeTool === tool.id ? ' is-active' : ''}`}
            onClick={() => onToolChange(tool.id)}
          >
            {t(tool.labelKey)}
          </button>
        ))}
      </div>

      <span className="viewer-divider" aria-hidden="true" />

      <div className="viewer-toolbar-group">
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          disabled={!canUndo}
          title={t('tool.undoTitle')}
          onClick={onUndo}
        >
          {t('tool.undo')}
        </button>
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          disabled={!hasSelection || !canEditSelection}
          title={t('tool.deleteTitle')}
          onClick={onDeleteSelected}
        >
          {t('common.delete')}
        </button>
        <button
          type="button"
          className="btn btn-primary btn-sm"
          disabled={!canSave || saving}
          title={t('tool.saveTitle')}
          onClick={onSave}
        >
          {saving ? t('common.saving') : t('common.save')}
        </button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={onOpenCalibration}>
          {t('tool.scale')}
          {calibrationLabel ? `: ${calibrationLabel}` : ''}
        </button>
      </div>
    </div>
  )
}
