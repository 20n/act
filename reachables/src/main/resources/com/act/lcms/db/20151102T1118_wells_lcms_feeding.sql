
-- A separate LCMS wells table for feeding experiments.  All wells in a feeding experiment may share an MSID/construct
-- and will have additional attributes beyond regular LCMS wells.
CREATE TABLE wells_lcms_feeding (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  msid varchar(63) NOT NULL,
  composition varchar(63) NOT NULL,
  extract varchar(255) DEFAULT NULL,
  chemical varchar(255) DEFAULT NULL,
  concentration float8 DEFAULT NULL,
  note text DEFAULT NULL
);
CREATE UNIQUE INDEX idx_wells_lcms_feeding_id ON wells_lcms_feeding(id);
CREATE UNIQUE INDEX idx_wells_lcms_feeding_plate_id_plate_column_plate_row ON wells_lcms_feeding(plate_id, plate_column, plate_row);
CREATE INDEX idx_wells_lcms_feeding_msid ON wells_lcms_feeding(msid);
CREATE INDEX idx_wells_lcms_feeding_composition ON wells_lcms_feeding(composition);
