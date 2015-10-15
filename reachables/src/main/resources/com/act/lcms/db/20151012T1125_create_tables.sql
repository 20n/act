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

CREATE TABLE wells_samples (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  msid varchar(63) NOT NULL,
  composition varchar(63) NOT NULL,
  chemical varchar(255) DEFAULT NULL,
  note text DEFAULT NULL
);
create unique index idx_wells_samples_id on wells_samples(id);
create unique index idx_wells_samples_plate_id_plate_column_plate_row on wells_samples(plate_id, plate_column, plate_row);
create index idx_wells_samples_msid on wells_samples(msid);
create index idx_wells_samples_composition on wells_samples(composition);

CREATE TABLE wells_standards (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  chemical varchar(255) DEFAULT NULL,
  media text DEFAULT NULL, -- TODO: should this be a foreign key?
  note text DEFAULT NULL
);
create unique index idx_wells_standards_id on wells_standards(id);
create unique index idx_wells_standards_plate_id_plate_column_plate_row on wells_standards(plate_id, plate_column, plate_row);
create index idx_wells_standards_chemical on wells_standards(chemical);

CREATE TABLE wells_delivered_strains (
  id SERIAL,
  plate_id integer NOT NULL references plates(id),
  plate_row integer NOT NULL,
  plate_column integer NOT NULL,
  well varchar(15) NOT NULL,
  msid varchar(63) NOT NULL,
  composition varchar(63) NOT NULL
);
create unique index idx_wells_delivered_strains_id on wells_delivered_strains(id);
create unique index idx_wells_delivered_strains_plate_id_plate_column_plate_row on wells_delivered_strains(plate_id, plate_column, plate_row);
create index idx_wells_delivered_strains_msid on wells_delivered_strains(msid);
create index idx_wells_delivered_strains_composition on wells_delivered_strains(composition);

CREATE TABLE wells_inductions (
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
create unique index idx_wells_inductions_id on wells_inductions(id);
create unique index idx_wells_inductions_plate_id_plate_column_plate_row on wells_inductions(plate_id, plate_column, plate_row);
create index idx_wells_inductions_msid on wells_inductions(msid);
create index idx_wells_inductions_composition on wells_inductions(composition);
create index idx_wells_inductions_chemical on wells_inductions(chemical);

CREATE TABLE wells_pregrowths (
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
create unique index idx_wells_pregrowths_id on wells_pregrowths(id);
create unique index idx_wells_pregrowths_plate_id_plate_column_plate_row on wells_pregrowths(plate_id, plate_column, plate_row);
create index idx_wells_pregrowths_source_plate_source_well on wells_pregrowths(source_plate, source_well);
create index idx_wells_pregrowths_msid on wells_pregrowths(msid);
create index idx_wells_pregrowths_composition on wells_pregrowths(composition);

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
