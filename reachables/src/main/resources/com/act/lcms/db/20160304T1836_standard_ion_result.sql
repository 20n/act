-- Add the manual_override column to curated_standard_metlin_ion so that we have a audit trail of manual overrides on
-- the standard ion result.
BEGIN;
ALTER TABLE standard_ion_result ADD manual_override integer DEFAULT NULL references curated_standard_metlin_ion(id);
COMMIT;
