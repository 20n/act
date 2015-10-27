-- CREATE the DB using CREATEdb before running these commands.

CREATE TABLE plates (
  id SERIAL,
  name varchar(255) NOT NULL,
  description text DEFAULT NULL,
  barcode varchar(255) DEFAULT NULL,
  location varchar(255) NOT NULL,
  plate_type varchar(255) NOT NULL, -- TODO: make this an enum?
  solvent varchar(255) DEFAULT NULL,
  temperature integer DEFAULT NULL,
  content_type varchar(63) NOT NULL
);
CREATE UNIQUE INDEX idx_plates_id ON plates(id);
CREATE UNIQUE INDEX idx_plates_name ON plates(name);
CREATE UNIQUE INDEX idx_plates_barcode ON plates(barcode);

CREATE TABLE wells_lcms (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  msid varchar(63) NOT NULL,
  composition varchar(63) NOT NULL,
  chemical varchar(255) DEFAULT NULL,
  note text DEFAULT NULL
);
CREATE UNIQUE INDEX idx_wells_lcms_id ON wells_lcms(id);
CREATE UNIQUE INDEX idx_wells_lcms_plate_id_plate_column_plate_row ON wells_lcms(plate_id, plate_column, plate_row);
CREATE INDEX idx_wells_lcms_msid ON wells_lcms(msid);
CREATE INDEX idx_wells_lcms_composition ON wells_lcms(composition);

CREATE TABLE wells_standard (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  chemical varchar(255) DEFAULT NULL,
  media text DEFAULT NULL, -- TODO: should this be a foreign key?
  note text DEFAULT NULL
);
CREATE UNIQUE INDEX idx_wells_standard_id ON wells_standard(id);
CREATE UNIQUE INDEX idx_wells_standard_plate_id_plate_column_plate_row ON wells_standard(plate_id, plate_column, plate_row);
CREATE INDEX idx_wells_standard_chemical ON wells_standard(chemical);

CREATE TABLE wells_delivered_strain (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  well varchar(15) NOT NULL,
  msid varchar(63) NOT NULL,
  composition varchar(63) NOT NULL
);
CREATE UNIQUE INDEX idx_wells_delivered_strain_id ON wells_delivered_strain(id);
CREATE UNIQUE INDEX idx_wells_delivered_strain_plate_id_plate_column_plate_row ON wells_delivered_strain(plate_id, plate_column, plate_row);
CREATE INDEX idx_wells_delivered_strain_msid ON wells_delivered_strain(msid);
CREATE INDEX idx_wells_delivered_strain_composition ON wells_delivered_strain(composition);

CREATE TABLE wells_induction (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  msid varchar(63) NOT NULL,
  chemical_source varchar(63) DEFAULT NULL,
  composition varchar(63) NOT NULL,
  chemical varchar(255) DEFAULT NULL,
  strain_source varchar(63) NOT NULL,
  note text DEFAULT NULL,
  growth integer DEFAULT NULL -- TODO: better define this field.
);
CREATE UNIQUE INDEX idx_wells_induction_id ON wells_induction(id);
CREATE UNIQUE INDEX idx_wells_induction_plate_id_plate_column_plate_row ON wells_induction(plate_id, plate_column, plate_row);
CREATE INDEX idx_wells_induction_msid ON wells_induction(msid);
CREATE INDEX idx_wells_induction_composition ON wells_induction(composition);
CREATE INDEX idx_wells_induction_chemical ON wells_induction(chemical);

CREATE TABLE wells_pregrowth (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  source_plate varchar(255) NOT NULL,
  source_well varchar(15) NOT NULL,
  msid varchar(63) NOT NULL,
  composition varchar(63) NOT NULL,
  note text DEFAULT NULL,
  growth integer DEFAULT NULL -- TODO: better define this field.
);
CREATE UNIQUE INDEX idx_wells_pregrowth_id ON wells_pregrowth(id);
CREATE UNIQUE INDEX idx_wells_pregrowth_plate_id_plate_column_plate_row ON wells_pregrowth(plate_id, plate_column, plate_row);
CREATE INDEX idx_wells_pregrowth_source_plate_source_well ON wells_pregrowth(source_plate, source_well);
CREATE INDEX idx_wells_pregrowth_msid ON wells_pregrowth(msid);
CREATE INDEX idx_wells_pregrowth_composition ON wells_pregrowth(composition);

CREATE TABLE chemicals_curated (
  id SERIAL,
  name varchar(255) NOT NULL,
  inchi text NOT NULL,
  mass float8 NOT NULL,
  expected_collision_voltage integer DEFAULT NULL,
  reference_url text DEFAULT NULL
);
CREATE UNIQUE INDEX idx_chemicals_curated_id ON chemicals_curated(id);
CREATE UNIQUE INDEX idx_chemicals_curated_name ON chemicals_curated(name);

CREATE TABLE constructs (
  id SERIAL,
  construct_id varchar(63) NOT NULL,
  target varchar(255) NOT NULL,
  host varchar(255) DEFAULT NULL
);
CREATE UNIQUE INDEX idx_constructs_id ON constructs(id);
CREATE UNIQUE INDEX idx_constructs_construct_id ON constructs(construct_id);
CREATE INDEX idx_constructs_target ON constructs(target);

CREATE TABLE scan_files (
  id SERIAL,
  filename varchar(255) NOT NULL,
  mode varchar(15) NOT NULL,
  file_type varchar(15) NOT NULL,
  plate_id integer DEFAULT NULL references plates(id),
  plate_row integer DEFAULT NULL,
  plate_column integer DEFAULT NULL
);
CREATE UNIQUE INDEX idx_scan_files_id ON scan_files(id);
CREATE UNIQUE INDEX idx_scan_files_filename ON scan_files(filename);
CREATE INDEX idx_scan_files_plate_id_plate_row_plate_column ON scan_files(plate_id, plate_row, plate_column);
