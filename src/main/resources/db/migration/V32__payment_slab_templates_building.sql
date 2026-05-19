-- Payment milestones are defined per building (Floor21 platform admin).

ALTER TABLE payment_slab_templates
    ADD COLUMN building_id UUID REFERENCES buildings (id);

UPDATE payment_slab_templates t
SET building_id = (
    SELECT b.id
    FROM buildings b
    WHERE b.builder_id = t.builder_id
    ORDER BY lower(b.building_name)
    LIMIT 1
)
WHERE building_id IS NULL;

INSERT INTO payment_slab_templates (id, sort_order, milestone_label, suggested_percent, active, created_at, builder_id, building_id)
SELECT gen_random_uuid(),
       p.sort_order,
       p.milestone_label,
       p.suggested_percent,
       p.active,
       NOW(),
       bld.builder_id,
       bld.id
FROM payment_slab_templates p
         CROSS JOIN buildings bld
         INNER JOIN builders br ON br.id = bld.builder_id
WHERE br.is_platform_admin = FALSE
  AND p.builder_id IN (SELECT id FROM builders WHERE is_platform_admin = TRUE);

DELETE FROM payment_slab_templates p
WHERE p.builder_id IN (SELECT id FROM builders WHERE is_platform_admin = TRUE);

DELETE FROM payment_slab_templates WHERE building_id IS NULL;

ALTER TABLE payment_slab_templates
    ALTER COLUMN building_id SET NOT NULL;

CREATE INDEX idx_payment_slab_templates_building ON payment_slab_templates (building_id);
