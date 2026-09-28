CREATE TABLE IF NOT EXISTS chat_sessions (
    id              UUID PRIMARY KEY,
    title           VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  UUID         NOT NULL
                     REFERENCES chat_sessions (id) ON DELETE CASCADE,
    role             VARCHAR(10)  NOT NULL
                     CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content          TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conv_created
    ON chat_messages (conversation_id, created_at);