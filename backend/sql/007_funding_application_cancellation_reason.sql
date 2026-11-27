USE funding;

ALTER TABLE funding_application
    ADD COLUMN cancellation_reason VARCHAR(200) NULL;