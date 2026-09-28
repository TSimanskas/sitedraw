CREATE TABLE documents (
    id              UUID PRIMARY KEY,
    project_id      UUID         NOT NULL,
    title           VARCHAR(255) NOT NULL,
    category        VARCHAR(100),
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_documents_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_documents_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE document_versions (
    id                  UUID PRIMARY KEY,
    document_id         UUID         NOT NULL,
    version_number      INT          NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    backup_storage_key  VARCHAR(500),
    page_count          INT          NOT NULL,
    file_size_bytes     BIGINT       NOT NULL,
    uploaded_by         UUID,
    uploaded_at         TIMESTAMP    NOT NULL,
    CONSTRAINT fk_document_versions_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_versions_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT uk_document_versions UNIQUE (document_id, version_number)
);

CREATE INDEX idx_documents_project ON documents (project_id);
