-- Add a column for concentration values, as a standards plate may
-- contain multiple concentrations of each chemical.
ALTER TABLE wells_standard ADD COLUMN concentration float8 DEFAULT NULL;
