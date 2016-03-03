CREATE TABLE ms1_for_well_and_mass_charge (
	id SERIAL,
	plate_id integer NOT NULL references plates(id),
  	plate_row integer NOT NULL,
  	plate_column integer NOT NULL,
  	use_snr boolean NOT NULL,
  	scan_file text NOT NULL,
  	chemical_name text NOT NULL,
  	metlin_ions text NOT NULL,
  	ions_to_spectra bytea NOT NULL,
  	ions_to_integral bytea NOT NULL,
  	ions_to_max bytea NOT NULL,
  	ions_to_log_snr bytea NOT NULL,
  	ions_to_avg_signal bytea NOT NULL,
  	ions_to_avg_ambient bytea NOT NULL,
  	individual_max_intensities bytea NOT NULL,
  	max_y_axis decimal NOT NULL
);
CREATE UNIQUE INDEX idx_ms1_for_well_and_mass_charge_id ON ms1_for_well_and_mass_charge(id);
CREATE UNIQUE INDEX idx_ms1_for_well_with_scan_file_metlin_ions ON ms1_for_well_and_mass_charge(plate_id, plate_row, plate_column, use_snr, scan_file, chemical_name, metlin_ions);
