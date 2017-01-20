package com.act.biointerpretation.networkanalysis;

import com.act.lcms.v2.ChemicalFormula;
import com.act.lcms.v2.Metabolite;
import com.act.lcms.v2.MolecularStructure;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Represents a metabolite in the metabolite network.
 * For now this class only stores an inchi, but in the future it can represent multiple levels of abstraction: a
 * structure in any format, a chemical formula, or only a mass.
 * TODO: Implement full serialization on LcmsMetabolite, and replace every use of InchiMetabolite with LcmsMetabolite.
 */
public class InchiMetabolite implements Metabolite {

  @JsonProperty("inchi")
  private String inchi;

  private MolecularStructure structure;

  @JsonCreator
  public InchiMetabolite(@JsonProperty("inchi") String inchi) {
    this.inchi = inchi;
    this.structure = new InchiStructure(inchi);
  }

  @JsonIgnore
  public String getInchi() {
    return inchi;
  }

  @JsonIgnore
  @Override
  public Optional<MolecularStructure> getStructure() {
    return Optional.of(structure);
  }

  @JsonIgnore
  @Override
  public Optional<ChemicalFormula> getFormula() {
    return Optional.empty();
  }

  @JsonIgnore
  @Override
  public Double getMonoIsotopicMass() {
    return structure.getMonoIsotopicMass();
  }
}
