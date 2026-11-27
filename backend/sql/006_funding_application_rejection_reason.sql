USE funding;

ALTER TABLE funding_application
    ADD COLUMN rejection_reason VARCHAR(200) NULL
    ;