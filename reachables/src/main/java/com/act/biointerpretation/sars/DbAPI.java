package com.act.biointerpretation.sars;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class DbAPI {

  private final MongoDB db;
  private Map<Long, Chemical> chemicalCache;
  private Map<Long, Reaction> reactionCache;

  public DbAPI(MongoDB db) {
    this.db = db;
    chemicalCache = new HashMap<>();
    reactionCache = new HashMap<>();
  }

  public void clearCaches() {
    chemicalCache = new HashMap<>();
    reactionCache = new HashMap<>();
  }

  public Chemical getChemical(Long id) {
    Chemical result = chemicalCache.get(id);
    if (result == null) {
      result = db.getChemicalFromChemicalUUID(id);
      chemicalCache.put(id, result);
    }
    return result;
  }

  public Reaction getReaction(Long id) {
    Reaction result = reactionCache.get(id);
    if (result == null) {
      result = db.getReactionFromUUID(id);
      reactionCache.put(id, result);
    }
    return result;
  }

  public List<Reaction> getReactions(ReactionGroup group) {
    List<Long> reactionIds = new ArrayList<>(group.getReactionIds());
    return Lists.transform(reactionIds, id -> getReaction(id));
  }

  public List<Molecule> getFirstProductsAsMolecules(List<Reaction> reactions) throws MolFormatException {
    List<Chemical> chemicals = getFirstProductsAsChemicals(reactions);
    try {
      return Lists.transform(chemicals, c -> c.importAsMolecule().get());
    } catch (NoSuchElementException e) {
      throw new MolFormatException("Couldn't import some chemical.");
    }
  }

  public List<Molecule> getFirstSubstratesAsMolecules(List<Reaction> reactions) throws MolFormatException {
    List<Chemical> chemicals = getFirstSubstratesAsChemicals(reactions);
    try {
      return Lists.transform(chemicals, c -> c.importAsMolecule().get());
    } catch (NoSuchElementException e) {
      throw new MolFormatException("Couldn't import some chemical.");
    }
  }

  public List<Chemical> getFirstProductsAsChemicals(List<Reaction> reactions) {
    return Lists.transform(reactions, r -> getFirstProductAsChemical(r));
  }

  public List<Chemical> getFirstSubstratesAsChemicals(List<Reaction> reactions) {
    return Lists.transform(reactions, r -> getFirstSubstrateAsChemical(r));
  }

  public Chemical getFirstSubstrateAsChemical(Reaction reaction) {
    return getChemical(reaction.getSubstrates()[0]);
  }

  public Chemical getFirstProductAsChemical(Reaction reaction) {
    return getChemical(reaction.getProducts()[0]);
  }

}
