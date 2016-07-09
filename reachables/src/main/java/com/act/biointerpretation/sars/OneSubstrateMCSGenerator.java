package com.act.biointerpretation.sars;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.chemaxon.search.mcs.MaxCommonSubstructure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class OneSubstrateMCSGenerator implements SarGenerator {

  private static final Integer ONLY_SUBSTRATE = 0;
  private static final String INCHI_SETTINGS = "inchi:AuxNone";
  private final MongoDB db;

  public OneSubstrateMCSGenerator(MongoDB db) {
    this.db = db;
  }

  @Override
  public Optional<Sar> getSar(SeqGroup group) throws IOException {
    Collection<Reaction> reactions = getReactions(group);

    // Can only build a SAR for exactly two reactions
    if (reactions.size() != 2) {
      return Optional.empty();
    }

    // Cannot only build a SAR if all reactions have exactly one substrate
    for (Reaction reaction : reactions) {
      if (reaction.getSubstrates().length != 1) {
        return Optional.empty();
      }
    }

    List<Molecule> molecules = new ArrayList<>(2);
    for (Reaction reaction : reactions) {
      Chemical chemical = db.getChemicalFromChemicalUUID(reaction.getSubstrates()[ONLY_SUBSTRATE]);
      molecules.add(MolImporter.importMol(chemical.getInChI(), INCHI_SETTINGS));
    }

    MaxCommonSubstructure mcs = MaxCommonSubstructure.newInstance();
    mcs.setMolecules(molecules.get(0), molecules.get(1));
    return Optional.of(new OneSubstrateSubstructureSar(mcs.nextResult().getAsMolecule()));
  }

  private Collection<Reaction> getReactions(SeqGroup group) {
    Collection<Reaction> reactions = new ArrayList<>();
    for (Long reactionId : group.getReactionIds()) {
      reactions.add(db.getReactionFromUUID(reactionId));
    }
    return reactions;
  }
}
