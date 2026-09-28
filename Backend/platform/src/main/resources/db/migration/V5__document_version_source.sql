ALTER TABLE document_versions
    ADD COLUMN source VARCHAR(32) DEFAULT 'ORIGINAL' NOT NULL;

UPDATE document_versions
SET source = CASE
                 WHEN version_number = 1 THEN 'ORIGINAL'
                 ELSE 'UPLOAD'
             END;
