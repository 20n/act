package com.act.biointerpretation.networkanalysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.v2.ChemicalFormula;
import com.act.lcms.v2.Metabolite;
import com.act.lcms.v2.MolecularStructure;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.NotImplementedException;

import java.util.Optional;

/**
 * Represents a metabolite in the metabolite network.
 * For now this class only stores an inchi, but in the future it can represent multiple levels of abstraction: a
 * structure in any format, a chemical formula, or only a mass.
 */
public class InchiMetabolite implements Metabolite {

  @JsonProperty("inchi")
  private String inchi;

  @JsonCreator
  public InchiMetabolite(@JsonProperty("inchi") String inchi) {
    this.inchi = inchi;
  }

  public String getInchi() {
    return inchi;
  }

  @Override
  public Optional<MolecularStructure> getStructure() {
    return Optional.of(new MolecularStructure() {
      @Override
      public String getInchi() {
        return inchi;
      }

      @Override
      public Boolean parseInchi(String inchi) {
        throw new NotImplementedException();
      }

      @Override
      public Double getMass() {
        return MassCalculator.calculateMass(inchi);
      }

      @Override
      public ChemicalFormula getChemicalFormula() {
        throw new NotImplementedException();
      }
    });
  }

  @Override
  public Optional<ChemicalFormula> getFormula() {
    return Optional.empty();
  }

  @Override
  public Double getMonoIsotopicMass() {
    return MassCalculator.calculateMass(inchi);
  }
}
