-- This table holds data about the best metlin ion chosen by the scientist for a given standard ion result, along with
-- notes on explainations.
CREATE TABLE curated_standard_metlin_ion (
    id SERIAL,
    created_at timestamp NOT NULL,
    author text NOT NULL,
    best_metlin_ion text NOT NULL,
    note text DEFAULT NULL,
    standard_ion_result_id integer NOT NULL references standard_ion_result(id)
);
CREATE UNIQUE INDEX idx_curated_standard_metlin_ion ON curated_standard_metlin_ion(id);
