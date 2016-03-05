CREATE TABLE curated_standard_metlin_ion (
    id SERIAL,
    created_at timestamp NOT NULL,
    author text NOT NULL,
    best_metlin_ion text NOT NULL,
    comments text NULL,
    standard_ion_result_id integer NOT NULL references standard_ion_result(id)
);
CREATE UNIQUE INDEX idx_curated_standard_metlin_ion ON curated_standard_metlin_ion(id);
