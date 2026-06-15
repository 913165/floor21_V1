CREATE TABLE milestone_sample_templates (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    file_name    VARCHAR(255) NOT NULL,
    file_content BYTEA NOT NULL,
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_milestone_sample_templates_sort ON milestone_sample_templates (sort_order, name);
