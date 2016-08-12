package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.function.Predicate;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OneSubstrateSubstructureSar.class, name = "OneSubstrateSubstructure"),
    @JsonSubTypes.Type(value = OneSubstrateCarbonCountSar.class, name = "CarbonCount"),
    @JsonSubTypes.Type(value = NoSar.class, name = "NoSar"),
})
public interface Sar extends Predicate<List<Molecule>> {

  /**
   * Test a given list of substrates to see whether this SAR will accept them.
   *
   * @param substrates The substrates of a chemical reaction.
   * @return True if this SAR can act on the given substrates.
   */
  boolean test(List<Molecule> substrates);
}
