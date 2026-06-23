ALTER TABLE builders
    ADD COLUMN IF NOT EXISTS docs_locker_pin_hash VARCHAR(255);

CREATE TABLE IF NOT EXISTS docs_locker_documents (
    id UUID PRIMARY KEY,
    builder_id UUID NOT NULL REFERENCES builders (id) ON DELETE CASCADE,
    booking_id UUID REFERENCES bookings (id) ON DELETE SET NULL,
    title VARCHAR(500),
    notes TEXT,
    original_filename VARCHAR(500) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(200),
    file_size_bytes BIGINT,
    uploaded_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_docs_locker_documents_builder_created
    ON docs_locker_documents (builder_id, created_at DESC);
