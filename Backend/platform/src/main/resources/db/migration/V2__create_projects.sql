CREATE TABLE projects (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    site_address    VARCHAR(500),
    client_name     VARCHAR(255),
    status          VARCHAR(50)  NOT NULL,
    start_date      DATE,
    end_date        DATE,
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE project_members (
    project_id      UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    assigned_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_project_members_user ON project_members (user_id);
