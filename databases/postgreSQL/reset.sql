-- =====================================================================
-- reset.sql : wipes everything the platform created in the database
--
-- Drops the two application schemas and all of their tables, types and data. Nothing outside
-- them is touched. Rebuild afterwards with schema.sql then seed-data.sql, or run
--   scripts/apply.sh --reset
-- DESTRUCTIVE: customer data, orders and logins are gone for good. Never run it on production.
-- =====================================================================

BEGIN;

DROP SCHEMA IF EXISTS trade CASCADE;
DROP SCHEMA IF EXISTS auth  CASCADE;

COMMIT;
