CREATE TABLE standard_ion_result (
    id SERIAL,
    chemical text NOT NULL,
    standard_well_id integer NOT NULL,
    negative_well_ids text NOT NULL,
    standard_ion_results text NOT NULL,
    plotting_result_paths text NOT NULL,
    best_metlin_ion text NOT NULL
);
CREATE UNIQUE INDEX idx_standard_ion_result_id ON standard_ion_result(id);
CREATE UNIQUE INDEX idx_standard_ion_result_with_chemical_standard_well_id_negative_well_ids_best_metlin_ion ON standard_ion_result(chemical, standard_well_id, negative_well_ids, best_metlin_ion);
