-- Payment milestones are owned by each builder (tenant).

ALTER TABLE payment_slab_templates
    ADD COLUMN builder_id UUID REFERENCES builders (id);

UPDATE payment_slab_templates t
SET builder_id = (SELECT b.id FROM builders b WHERE b.email = 'admin@skylinehomes.com' LIMIT 1)
WHERE t.builder_id IS NULL;

-- Copy Skyline seed milestones to other non–platform builders so they start with the same defaults.
INSERT INTO payment_slab_templates (id, sort_order, milestone_label, suggested_percent, active, created_at, builder_id)
SELECT gen_random_uuid(),
       p.sort_order,
       p.milestone_label,
       p.suggested_percent,
       p.active,
       NOW(),
       b.id
FROM builders b
CROSS JOIN payment_slab_templates p
WHERE b.is_platform_admin = FALSE
  AND b.id <> (SELECT id FROM builders bb WHERE bb.email = 'admin@skylinehomes.com' LIMIT 1)
  AND p.builder_id = (SELECT id FROM builders bb WHERE bb.email = 'admin@skylinehomes.com' LIMIT 1);

ALTER TABLE payment_slab_templates
    ALTER COLUMN builder_id SET NOT NULL;

CREATE INDEX idx_payment_slab_templates_builder ON payment_slab_templates (builder_id);
