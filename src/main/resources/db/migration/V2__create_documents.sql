CREATE TABLE IF NOT EXISTS documents (
    id            UUID PRIMARY KEY,
    filename      VARCHAR(512)  NOT NULL,
    content_type  VARCHAR(255)  NOT NULL,
    size_bytes    BIGINT        NOT NULL CHECK (size_bytes >= 0),
    checksum      CHAR(64)      NOT NULL,          -- SHA-256 hex
    chunk_count   INTEGER       NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL
                  CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    failure_reason TEXT,
    uploaded_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_documents_checksum ON documents (checksum);

-- Option A: partial unique index (only READY/PROCESSING docs must be unique)
CREATE UNIQUE INDEX IF NOT EXISTS ux_documents_checksum_active
    ON documents (checksum) WHERE status <> 'FAILED';