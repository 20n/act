package com.act.lcms.v2;


import com.act.biointerpretation.networkanalysis.InchiMetabolite;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Optional;

/**
 * Representation of a metabolite
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = InchiMetabolite.class, name = "inchi_metabolite")
})
public interface Metabolite {

  /**
   * Returns an optional structure
   */
  Optional<MolecularStructure> getStructure();

  /**
   * Returns an optional formula
   */
  Optional<ChemicalFormula> getFormula();

  /**
   * Returns the metabolite's mono-isotopic mass (in Da)
   */
  Double getMonoIsotopicMass();
}
