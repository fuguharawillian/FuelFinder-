ALTER TABLE anp_import_logs
    DROP CONSTRAINT ck_anp_logs_status,
    ADD COLUMN rows_imported_from_sao_paulo INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN rows_ignored_other_states INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN invalid_rows INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN stations_created INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN stations_updated INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN prices_associated INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN api_cnpjs_unmatched INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN api_stations_without_coordinates INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN stations_without_coordinates INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN coordinates_updated INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN api_pages_processed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN progress_stage VARCHAR(40) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN progress_message VARCHAR(255),
    ADD COLUMN progress_percent INTEGER,
    ADD CONSTRAINT ck_anp_logs_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED'));

UPDATE anp_import_logs
SET progress_stage = CASE
        WHEN status = 'FAILED' THEN 'FAILED'
        ELSE 'COMPLETED'
    END,
    progress_percent = CASE
        WHEN status = 'FAILED' THEN NULL
        ELSE 100
    END;
