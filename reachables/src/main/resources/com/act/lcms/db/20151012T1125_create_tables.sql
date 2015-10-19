-- Create the DB using createdb before running these commands.

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
create unique index idx_plates_id on plates(id);
create unique index idx_plates_name on plates(name);
create unique index idx_plates_barcode on plates(barcode);

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
create unique index idx_wells_lcms_id on wells_lcms(id);
create unique index idx_wells_lcms_plate_id_plate_column_plate_row on wells_lcms(plate_id, plate_column, plate_row);
create index idx_wells_lcms_msid on wells_lcms(msid);
create index idx_wells_lcms_composition on wells_lcms(composition);

CREATE TABLE wells_standard (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  chemical varchar(255) DEFAULT NULL,
  media text DEFAULT NULL, -- TODO: should this be a foreign key?
  note text DEFAULT NULL
);
create unique index idx_wells_standard_id on wells_standard(id);
create unique index idx_wells_standard_plate_id_plate_column_plate_row on wells_standard(plate_id, plate_column, plate_row);
create index idx_wells_standard_chemical on wells_standard(chemical);

CREATE TABLE wells_delivered_strain (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  well varchar(15) NOT NULL,
  msid varchar(63) NOT NULL,
  composition varchar(63) NOT NULL
);
create unique index idx_wells_delivered_strain_id on wells_delivered_strain(id);
create unique index idx_wells_delivered_strain_plate_id_plate_column_plate_row on wells_delivered_strain(plate_id, plate_column, plate_row);
create index idx_wells_delivered_strain_msid on wells_delivered_strain(msid);
create index idx_wells_delivered_strain_composition on wells_delivered_strain(composition);

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
create unique index idx_wells_induction_id on wells_induction(id);
create unique index idx_wells_induction_plate_id_plate_column_plate_row on wells_induction(plate_id, plate_column, plate_row);
create index idx_wells_induction_msid on wells_induction(msid);
create index idx_wells_induction_composition on wells_induction(composition);
create index idx_wells_induction_chemical on wells_induction(chemical);

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
create unique index idx_wells_pregrowth_id on wells_pregrowth(id);
create unique index idx_wells_pregrowth_plate_id_plate_column_plate_row on wells_pregrowth(plate_id, plate_column, plate_row);
create index idx_wells_pregrowth_source_plate_source_well on wells_pregrowth(source_plate, source_well);
create index idx_wells_pregrowth_msid on wells_pregrowth(msid);
create index idx_wells_pregrowth_composition on wells_pregrowth(composition);

CREATE TABLE chemicals_curated (
  id SERIAL,
  name varchar(255) NOT NULL,
  inchi text NOT NULL,
  m_plus_h_plus_mass float8 NOT NULL,
  expected_collision_voltage integer DEFAULT NULL,
  reference_url text DEFAULT NULL
);
create unique index idx_chemicals_curated_id on chemicals_curated(id);
create unique index idx_chemicals_curated_name on chemicals_curated(name);
create unique index idx_chemicals_curated_inchi on chemicals_curated(inchi);

CREATE TABLE constructs (
  id SERIAL,
  construct_id varchar(63) NOT NULL,
  target varchar(255) NOT NULL,
  host varchar(255) DEFAULT NULL
);
create unique index idx_constructs_id on constructs(id);
create unique index idx_constructs_construct_id on constructs(construct_id);
create index idx_constructs_target on constructs(target);

CREATE TABLE scan_files (
  id SERIAL,
  filename varchar(255) NOT NULL,
  mode varchar(15) NOT NULL,
  file_type varchar(15) NOT NULL,
  plate_id integer DEFAULT NULL references plates(id),
  plate_row integer DEFAULT NULL,
  plate_column integer DEFAULT NULL
);
create unique index idx_scan_files_id on scan_files(id);
create unique index idx_scan_files_filename on scan_files(filename);
create index idx_scan_files_plate_id on scan_files(plate_id);
create index idx_scan_files_plate_row_plate_column on scan_files(plate_row, plate_column);
