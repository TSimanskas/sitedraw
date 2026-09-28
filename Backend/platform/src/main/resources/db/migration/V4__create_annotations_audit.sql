ALTER TABLE document_versions
    ADD COLUMN calibration_pixels_per_unit DOUBLE;
ALTER TABLE document_versions
    ADD COLUMN calibration_unit_label VARCHAR(20);

CREATE TABLE annotations (
    id                  UUID PRIMARY KEY,
    document_version_id UUID         NOT NULL,
    page_number         INT          NOT NULL,
    type                VARCHAR(30)  NOT NULL,
    data                JSON         NOT NULL,
    created_by          UUID         NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    CONSTRAINT fk_annotations_document_version FOREIGN KEY (document_version_id) REFERENCES document_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_annotations_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE annotation_revisions (
    id              UUID PRIMARY KEY,
    annotation_id   UUID         NOT NULL,
    action          VARCHAR(20)  NOT NULL,
    before_state    JSON,
    after_state     JSON,
    changed_by      UUID         NOT NULL,
    changed_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_annotation_revisions_annotation FOREIGN KEY (annotation_id) REFERENCES annotations (id) ON DELETE CASCADE,
    CONSTRAINT fk_annotation_revisions_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
);

CREATE TABLE audit_events (
    id                  UUID PRIMARY KEY,
    user_id             UUID,
    action              VARCHAR(50)  NOT NULL,
    entity_type         VARCHAR(50)  NOT NULL,
    entity_id           UUID,
    project_id          UUID,
    document_version_id UUID,
    before_state        JSON,
    after_state         JSON,
    ip_address          VARCHAR(45),
    created_at          TIMESTAMP    NOT NULL,
    CONSTRAINT fk_audit_events_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_audit_events_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL
);

CREATE INDEX idx_annotations_document_version_page ON annotations (document_version_id, page_number);
CREATE INDEX idx_annotation_revisions_annotation ON annotation_revisions (annotation_id, changed_at DESC);
CREATE INDEX idx_audit_events_project ON audit_events (project_id, created_at DESC);
CREATE INDEX idx_audit_events_document_version ON audit_events (document_version_id, created_at DESC);
