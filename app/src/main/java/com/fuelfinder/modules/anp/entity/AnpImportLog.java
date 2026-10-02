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

    @Column(name = "rows_imported_from_sao_paulo", nullable = false)
    private Integer rowsImportedFromSaoPaulo = 0;

    @Column(name = "rows_ignored_other_states", nullable = false)
    private Integer rowsIgnoredOtherStates = 0;

    @Column(name = "invalid_rows", nullable = false)
    private Integer invalidRows = 0;

    @Column(name = "stations_created", nullable = false)
    private Integer stationsCreated = 0;

    @Column(name = "stations_updated", nullable = false)
    private Integer stationsUpdated = 0;

    @Column(name = "prices_associated", nullable = false)
    private Integer pricesAssociated = 0;

    @Column(name = "api_cnpjs_unmatched", nullable = false)
    private Integer apiCnpjsUnmatched = 0;

    @Column(name = "api_stations_without_coordinates", nullable = false)
    private Integer apiStationsWithoutCoordinates = 0;

    @Column(name = "stations_without_coordinates", nullable = false)
    private Integer stationsWithoutCoordinates = 0;

    @Column(name = "coordinates_updated", nullable = false)
    private Integer coordinatesUpdated = 0;

    @Column(name = "api_pages_processed", nullable = false)
    private Integer apiPagesProcessed = 0;

    @Column(name = "progress_stage", nullable = false, length = 40)
    private String progressStage = "QUEUED";

    @Column(name = "progress_message", length = 255)
    private String progressMessage;

    @Column(name = "progress_percent")
    private Integer progressPercent;

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

    public Integer getRowsImportedFromSaoPaulo() {
        return rowsImportedFromSaoPaulo;
    }

    public Integer getRowsIgnoredOtherStates() {
        return rowsIgnoredOtherStates;
    }

    public Integer getInvalidRows() {
        return invalidRows;
    }

    public Integer getStationsCreated() {
        return stationsCreated;
    }

    public Integer getStationsUpdated() {
        return stationsUpdated;
    }

    public Integer getPricesAssociated() {
        return pricesAssociated;
    }

    public Integer getApiCnpjsUnmatched() {
        return apiCnpjsUnmatched;
    }

    public Integer getApiStationsWithoutCoordinates() {
        return apiStationsWithoutCoordinates;
    }

    public Integer getStationsWithoutCoordinates() {
        return stationsWithoutCoordinates;
    }

    public Integer getCoordinatesUpdated() {
        return coordinatesUpdated;
    }

    public Integer getApiPagesProcessed() {
        return apiPagesProcessed;
    }

    public String getProgressStage() {
        return progressStage;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    public Integer getProgressPercent() {
        return progressPercent;
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

    public void setRowsImportedFromSaoPaulo(Integer rowsImportedFromSaoPaulo) {
        this.rowsImportedFromSaoPaulo = rowsImportedFromSaoPaulo;
    }

    public void setRowsIgnoredOtherStates(Integer rowsIgnoredOtherStates) {
        this.rowsIgnoredOtherStates = rowsIgnoredOtherStates;
    }

    public void setInvalidRows(Integer invalidRows) {
        this.invalidRows = invalidRows;
    }

    public void setStationsCreated(Integer stationsCreated) {
        this.stationsCreated = stationsCreated;
    }

    public void setStationsUpdated(Integer stationsUpdated) {
        this.stationsUpdated = stationsUpdated;
    }

    public void setPricesAssociated(Integer pricesAssociated) {
        this.pricesAssociated = pricesAssociated;
    }

    public void setApiCnpjsUnmatched(Integer apiCnpjsUnmatched) {
        this.apiCnpjsUnmatched = apiCnpjsUnmatched;
    }

    public void setApiStationsWithoutCoordinates(Integer apiStationsWithoutCoordinates) {
        this.apiStationsWithoutCoordinates = apiStationsWithoutCoordinates;
    }

    public void setStationsWithoutCoordinates(Integer stationsWithoutCoordinates) {
        this.stationsWithoutCoordinates = stationsWithoutCoordinates;
    }

    public void setCoordinatesUpdated(Integer coordinatesUpdated) {
        this.coordinatesUpdated = coordinatesUpdated;
    }

    public void setApiPagesProcessed(Integer apiPagesProcessed) {
        this.apiPagesProcessed = apiPagesProcessed;
    }

    public void setProgressStage(String progressStage) {
        this.progressStage = progressStage;
    }

    public void setProgressMessage(String progressMessage) {
        this.progressMessage = progressMessage;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}
