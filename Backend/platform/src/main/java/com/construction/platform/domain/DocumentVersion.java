package com.construction.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "backup_storage_key")
    private String backupStorageKey;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "calibration_pixels_per_unit")
    private Double calibrationPixelsPerUnit;

    @Column(name = "calibration_unit_label")
    private String calibrationUnitLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentVersionSource source = DocumentVersionSource.ORIGINAL;

    public DocumentVersion(
            Document document,
            int versionNumber,
            String storageKey,
            String backupStorageKey,
            int pageCount,
            long fileSizeBytes,
            User uploadedBy
    ) {
        this.id = UUID.randomUUID();
        this.document = document;
        this.versionNumber = versionNumber;
        this.storageKey = storageKey;
        this.backupStorageKey = backupStorageKey;
        this.pageCount = pageCount;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onUpload() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }
}
