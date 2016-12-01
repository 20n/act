package com.act.reachables;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import act.shared.Chemical;
import act.shared.Reaction;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ActData implements Serializable {
  private static final long serialVersionUID = -1016179639411233872L;

  ConditionalReachable _LastReachabilityComputation = null;
  Network Act;
  Network ActTree;

  List<Long> allrxnids;                            // sorted list of all reaction uuids (from db.actfamilies)
  Set<Long> chemsReferencedInRxns;                 // every chemid referenced in cofactors, natives, or any reaction in DB
  Set<Long> cofactors;                             // chemicals with isCofactor : true in DB
  Set<Long> natives;                               // chemicals marked as isNative : true in DB
  Set<Long> metaCycBigMolsOrRgrp;                  // chemicals whose inchi matches db.chemicals.find({InChI:/FAKE/})

  HashMap<String, List<Long>> chemicalsWithUserField;  // if a user asks us to output an artificial
                                                       // subset of chemicals that have certain fields,
                                                       // e.g., xref.CHEBI, xref.DEA etc.
  Set<Long> chemicalsWithUserField_treeOrganic;    // in the final tree; these nodes were reachable organically
  Set<Long> chemicalsWithUserField_treeArtificial; // in the final tree; these nodes were added artificially as
                                                   // they were not organically reachable

  HashMap<Long, Boolean> chemIdIsAbstraction;      // the chemicals that have R in inchis and therefore abstractions
  HashMap<Long, String> chemId2Inchis;             // map chemid -> inchi
  HashMap<Long, String> chemId2ReadableName;       // map chemid -> name
  HashMap<String, Long> chemInchis;                // reverse index of inchi -> chemid
  HashMap<Long, Set<Integer>> chemToxicity;        // If the chemical has xref.DRUGBANK.metadata.toxicity with LD50 [1]
  HashMap<Long, Node> chemsInAct;                  // map of chemicals seen in any rxn -> its node object in network
  HashMap<Pair<Long, Long>, Edge> rxnsInAct;       // map of rxns (exploded to all pairs bw sub x prod) to edge in network
  HashMap<Long, Set<Long>> rxnSubstrates;          // rxnid -> non-cofactor substrates
  HashMap<Long, Set<Long>> rxnSubstratesCofactors; // rxnid -> cofactor substrates
  HashMap<Long, Set<Long>> rxnProducts;            // rxnid -> non-cofactor products
  HashMap<Long, Set<Long>> rxnProductsCofactors;   // rxnid -> cofactor products
  HashMap<Long, Set<Long>> rxnOrganisms;           // rxnid -> set of organism ids associated with rxn
  HashMap<Long, Set<Long>> rxnsThatConsumeChem;    // non-cofactor chemicals -> rxns that have them as substrates
  HashMap<Long, Set<Long>> rxnsThatProduceChem;    // non-cofactor chemicals -> rxns that have them as products
  HashMap<Long, Boolean> rxnHasSeq;                // do we know an enzyme catalyzing this rxn?

  // The raw dataset comes in with multiple reactions
  // with the same chemistry, i.e., the same substrates
  // and products. Since reactions with the same chemistry
  // will be semantically equivalent in the reachables computation
  // i.e., they will lead to the same expansion, we call them
  // a class, where a class is defined as P(substrate_set, product_set)
  // (see LoadAct.addToNw where we create and use this "class id")
  //
  // The expansion code picks between raw rxns or classes
  // on the basis of the parameter GlobalParams.USE_RXN_CLASSES
  //
  // Expansion in WavefrontExpansion.{computeRxnNeeds, productsOf},
  // picks either the classes or the raw rxns to expand over.
  //
  // The first three below are used in LoadAct and WavefrontExpansion
  // and the remaining two are for when we are dumping out cascade
  // metadata in scala/reachables.scala

  HashMap<Long, Set<Long>> rxnClassesSubstrates;   // rxnid -> non-cofactor substrates (representative rxns that form classes)
  HashMap<Long, Set<Long>> rxnClassesProducts;     // rxnid -> non-cofactor products (representative rxns that form classes)
  Set<Pair<Set<Long>, Set<Long>>> rxnClasses;      // set for classes (substrates, products)

  HashMap<Long, Set<Long>> rxnClassesThatConsumeChem;    // non-cofactor chemicals -> rxns that have them as substrates
  HashMap<Long, Set<Long>> rxnClassesThatProduceChem;    // non-cofactor chemicals -> rxns that have them as products

  HashMap<Long, List<Long>> noSubstrateRxnsToProducts; // product rxns that only depend on cofactors

  /* Hack to work around the fact that static fields don't get serialized.  Edge and Node both now call
   * ActData.instance() a lot, which is gross but hopefully functional. */
  HashMap<Long, List<Node>> nodeCache = new HashMap<Long, List<Node>>();
  HashMap<Long, HashMap<String, Serializable>> nodeAttributes = new HashMap<>();

  HashMap<Edge, Edge> edgeCache = new HashMap<>();
  HashMap<Edge, HashMap<String, Serializable>> edgeAttributes = new HashMap<>();

  private static ActData _instance = null;
  public static ActData instance() {
    if (ActData._instance == null)
      ActData._instance = new ActData();
    return ActData._instance;
  }

  public void serialize(String toFile) {
    try {
      OutputStream file = new FileOutputStream(toFile);
      OutputStream buffer = new BufferedOutputStream(file);
      ObjectOutput output = new ObjectOutputStream(buffer);
      try {
        output.writeObject(_instance);
      } finally {
        output.close();
      }
    } catch(IOException ex) {
      throw new RuntimeException("ActData serialize failed: " + ex);
    }
  }

  public void deserialize(String fromFile) {
    try {
      InputStream file = new FileInputStream(fromFile);
      InputStream buffer = new BufferedInputStream(file);
      ObjectInput input = new ObjectInputStream (buffer);
      try {
        ActData._instance = (ActData)input.readObject();
      } finally {
        input.close();
      }
    } catch(ClassNotFoundException ex) {
      throw new RuntimeException("ActData deserialize failed: Class not found: " + ex);
    } catch(IOException ex) {
      throw new RuntimeException("ActData deserialize failed: IO problem: " + ex);
    }
  }

  public Network getActTree() {
    return this.ActTree;
  }

  public String mapChemId2Inchis(Long id) {
    return this.chemId2Inchis.get(id);
  }

  public String mapChemId2ReadableName(Long id) {
    return this.chemId2ReadableName.get(id);
  }
}
