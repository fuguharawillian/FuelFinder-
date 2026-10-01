package com.fuelfinder.modules.anp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "anp_import_logs")
public class AnpImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "reference_period", nullable = false, length = 20)
    private String referencePeriod;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "import_start", nullable = false)
    private LocalDateTime importStart;

    @Column(name = "import_end")
    private LocalDateTime importEnd;

    @Column(name = "total_records_read", nullable = false)
    private Integer totalRecordsRead = 0;

    @Column(name = "total_records_imported", nullable = false)
    private Integer totalRecordsImported = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status = ImportStatus.FAILED;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AnpImportLog() {
    }

    public AnpImportLog(
            String fileName,
            String referencePeriod,
            String sourceUrl,
            LocalDateTime importStart,
            UUID triggeredBy) {
        this.fileName = fileName;
        this.referencePeriod = referencePeriod;
        this.sourceUrl = sourceUrl;
        this.importStart = importStart;
        this.triggeredBy = triggeredBy;
    }

    @PrePersist
    void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getReferencePeriod() {
        return referencePeriod;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDateTime getImportStart() {
        return importStart;
    }

    public LocalDateTime getImportEnd() {
        return importEnd;
    }

    public Integer getTotalRecordsRead() {
        return totalRecordsRead;
    }

    public Integer getTotalRecordsImported() {
        return totalRecordsImported;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public UUID getTriggeredBy() {
        return triggeredBy;
    }

    public void setImportEnd(LocalDateTime importEnd) {
        this.importEnd = importEnd;
    }

    public void setTotalRecordsRead(Integer totalRecordsRead) {
        this.totalRecordsRead = totalRecordsRead;
    }

    public void setTotalRecordsImported(Integer totalRecordsImported) {
        this.totalRecordsImported = totalRecordsImported;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}
