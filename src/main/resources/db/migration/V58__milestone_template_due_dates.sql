-- Common template due date per building (editable on Milestone setup) and optional per-template row date.

ALTER TABLE buildings
    ADD COLUMN milestone_template_due_date DATE;

ALTER TABLE slabs
    ADD COLUMN default_due_date DATE;
