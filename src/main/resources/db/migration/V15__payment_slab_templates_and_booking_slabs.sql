CREATE TABLE payment_slab_templates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sort_order        INTEGER        NOT NULL,
    milestone_label   VARCHAR(800)   NOT NULL,
    suggested_percent DECIMAL(9, 4)  NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_slab_templates_sort ON payment_slab_templates (sort_order);
CREATE INDEX idx_payment_slab_templates_active ON payment_slab_templates (active);

CREATE TABLE booking_payment_slabs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID           NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    template_id       UUID           NULL REFERENCES payment_slab_templates (id) ON DELETE SET NULL,
    sort_order        INTEGER        NOT NULL,
    milestone_label   VARCHAR(800)   NOT NULL,
    due_date          DATE           NULL,
    percent           DECIMAL(9, 4)  NULL,
    extra_amount      DECIMAL(15, 2) NOT NULL DEFAULT 0,
    agreed_amount     DECIMAL(15, 2) NULL,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_booking_payment_slabs_booking ON booking_payment_slabs (booking_id);

INSERT INTO payment_slab_templates (sort_order, milestone_label, suggested_percent, active, created_at)
VALUES (1, 'Initial booking amount', 10, true, NOW()),
       (2, 'On or after execution of Agreement', 15, true, NOW());
