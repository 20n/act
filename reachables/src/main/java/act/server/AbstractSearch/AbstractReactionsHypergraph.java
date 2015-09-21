package act.server.AbstractSearch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;

import act.render.RenderChemical;
import act.render.RenderReactions;

import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.helpers.P;

public class AbstractReactionsHypergraph {
  /**
   *  Maps reaction to its required reactants
   */
  private Map<String, Set<String>> reactionReactants;

  /**
   *  The reverse mapping of the above
   */
  private Map<String, Set<String>> reactantReactions;

  /**
   *  Maps products to reactions that produce it
   */
  private Map<String, Set<String>> productReactions;

  private Set<String> chemicals;
  private Set<String> reactions;

  private IdType myIdType;

  public enum IdType {
    DB_ID,
    SMILES,
    CARBON_SKELETON,
    ERO
  }

  public AbstractReactionsHypergraph() {
    reactionReactants = new HashMap<String, Set<String>>();
    reactantReactions = new HashMap<String, Set<String>>();
    productReactions = new HashMap<String, Set<String>>();
    reactions = new HashSet<String>();
    chemicals = new HashSet<String>();
    myIdType = IdType.SMILES;
  }

  /**
   * The idType should be set depending on what is used as the chemical ids.
   * Depending on what it is set as, they will be treated differently when producing a dot file
   * with writeDOT.
   */
  public void setIdType(IdType idtype) {
    myIdType = idtype;
  }

  public void addReaction(String reaction, Collection<String> reactants, Collection<String> products) {
    addReactants(reaction, reactants);
    Set<String> reactionSet = new HashSet<String>();
    reactionSet.add(reaction);
    for (String product : products)
      addReactions(product, reactionSet);
  }


  public void addReactants(Long reactionID, Collection<Long> reactants) {
    Set<String> reactantStrings = new HashSet<String>();
    for (Long r : reactants) {
      reactantStrings.add(r.toString());
    }
    addReactants(reactionID.toString(), reactantStrings);
  }

  /**
   * Adds edges between the reactants and the reaction.
   * @param reaction
   * @param reactants
   */
  public void addReactants(String reaction, Collection<String> reactants) {
    this.chemicals.addAll(reactants);
    this.reactions.add(reaction);
    if (!reactionReactants.containsKey(reaction)) {
      reactionReactants.put(reaction, new HashSet<String>());
    }
    reactionReactants.get(reaction).addAll(reactants);

    for (String reactant : reactants) {
      if(!reactantReactions.containsKey(reactant)) {
        reactantReactions.put(reactant, new HashSet<String>());
      }
      reactantReactions.get(reactant).add(reaction);
    }
  }

  public void addReactions(Long productID, Collection<Long> reactions) {
    Set<String> reactionStrings = new HashSet<String>();
    for (Long r : reactions) {
      reactionStrings.add(r.toString());
    }
    addReactions(productID.toString(), reactionStrings);
  }

  /**
   * Adds edges between the a chemical and the bag of reactions that produce it.
   * @param productID
   * @param reactions
   */
  public void addReactions(String productID, Collection<String> reactions) {
    this.chemicals.add(productID);
    this.reactions.addAll(reactions);
    if (!productReactions.containsKey(productID)) {
      productReactions.put(productID, new HashSet<String>());
    }
    productReactions.get(productID).addAll(reactions);
  }

  public Set<P<Long, Long>> getReactionReactantEdges() {
    Set<P<Long, Long>> edges = new HashSet<P<Long, Long>>();
    for (String reaction: reactionReactants.keySet()) {
      Set<String> reactants = reactionReactants.get(reaction);
      for (String reactant : reactants) {
        edges.add(new P<Long, Long>(Long.parseLong(reaction), Long.parseLong(reactant)));
      }
    }
    return edges;
   }

  public Set<P<Long, Long>> getProductReactionEdges() {
    Set<P<Long, Long>> edges = new HashSet<P<Long, Long>>();
    for (String product: productReactions.keySet()) {
      Set<String> reactions = productReactions.get(product);
      for (String reaction : reactions) {
        edges.add(new P<Long, Long>(Long.parseLong(product), Long.parseLong(reaction)));
      }
    }
    return edges;
   }

  private String fileSafe(String fnameProposed){
    return fnameProposed.replace("/", "_");
  }

  /**
   * See below.
   * @param fname
   * @param db
   * @throws IOException
   */
  public void writeDOT(String fname, MongoDB db) throws IOException {
    this.writeDOT("", fname, db, false);
  }

  /**
   * Given file name, outputs graph in dot format.
   * @param fname
   * @param db
   * @param genChemicalImages   Generate chemical images Graphviz can include in the graph.
   *                 The images will be in a dir called ReactionsHypergraphImages.
   * @throws IOException
   */
  public void writeDOT(String folder, String fname, MongoDB db, boolean genChemicalImages) throws IOException {
    if (genChemicalImages) {
      File file = new File(folder+"/ReactionsHypergraphImages");
      file.mkdirs();
    }

    /*
    System.out.println("-----------------");
    System.out.println(reactionReactants);
    System.out.println(reactantReactions);
    System.out.println(productReactions);
    System.out.println(reactions);
    System.out.println(chemicals);
    System.out.println("-----------------");
    */

    BufferedWriter dotf = new BufferedWriter(new FileWriter(folder+"/"+fname, false)); // open for overwrite
    // taking hints from http://en.wikipedia.org/wiki/DOT_language#A_simple_example
    String graphname = "paths";

    //We want to split certain compounds into different graph nodes for neatness.
    Set<String> compoundsToSplit = new HashSet<String>();

    dotf.write("digraph " + graphname + " {\n");
    dotf.write("\tnode [shape=plaintext]\n");

    for (String chemicalID : chemicals) {
      Long c;
      String idName = "";
      String color = "";
      String img = "";
      String smiles = null;

      if (!productReactions.containsKey(chemicalID)) {
        color = "fontcolor = \"red\", ";
      }

      if (genChemicalImages){
        idName = "label=\"\"";
      }
      else {
        idName = "label=\"" + chemicalID + "\"";
      }

      if (myIdType == IdType.CARBON_SKELETON) {
        String[] smilesSplit = chemicalID.split("_");
        try {
          smiles = smilesSplit[1];
        }
        catch (Exception e){
          //in this case, we had no molecule
          smiles = "";
        }
      }
      else if (myIdType == IdType.ERO) {
          Indigo ind = new Indigo();
          IndigoInchi ic = new IndigoInchi(ind);
          try {
            smiles = ic.loadMolecule(chemicalID).canonicalSmiles();
          } catch(Exception e) {
            System.out.println("Failed to find SMILES for: " + chemicalID);
          }
      }
      if (genChemicalImages && smiles != null) {
        String imgFilename = "ReactionsHypergraphImages/" + fileSafe(chemicalID) + ".png";
        String fullImgFilename = folder+"/"+imgFilename;
        //RenderChemical.renderToFile(imgFilename, smiles);
        Indigo indigo = new Indigo();
        SMILES.renderMolecule(indigo.loadMolecule(smiles), fullImgFilename, "", indigo);
        img = ", image=\"" + imgFilename + "\"";
        //System.out.println("Wrote chemical image to "+imgFilename);
      }


      if (compoundsToSplit.contains(chemicalID)) {
        for (String reaction : reactantReactions.get(chemicalID)) {
          dotf.write("c" + chemicalID + "_" + reaction + " [" + color + idName + "]\n");
        }
      } else {
        dotf.write("\"c" + chemicalID + "\" [" + color + idName + img + "]\n");
      }
    }

    for (String r : reactions) {
      String img = "";

      if (genChemicalImages) {
        String imgFilename = "ReactionsHypergraphImages/" + fileSafe(r) + ".png";
        String fullImgFilename = folder+"/"+imgFilename;
        try {
          Long reactionID = Long.parseLong(r);
          RenderReactions.renderByRxnID((long) reactionID, fullImgFilename, null, db);
          img = ", image=\"" + imgFilename + "\"";
          //System.out.println("Wrote reaction image to "+imgFilename);
        }
        catch (Exception e){
          //System.out.println(e);
          //System.out.println("Tried to use reaction as id when it's not an id");
        }
        try {
          Indigo indigo = new Indigo();
          SMILES.renderReaction(indigo.loadQueryReaction(r), fullImgFilename, "", indigo);
        }
        catch (Exception e){
          //System.out.println(e);
          //System.out.println("Tried to use reaction as inchi when it's not an inchi");
        }
      }

      dotf.write("\"r" + r + "\" [label=\"" + r + "\""+img+"]\n");
    }


    //Write edges
    int edges = 0;
    for (String reactionID: reactionReactants.keySet()) {
      for (String reactantID : reactionReactants.get(reactionID)) {
        if (compoundsToSplit.contains(reactantID))
          dotf.write("\t" + "c" + reactantID + "_" + reactionID + "->" + "\"r" + reactionID + "\";\n");
        else
          dotf.write("\t" + "\"c" + reactantID + "\"->" + "\"r" + reactionID + "\";\n");
        edges++;
      }
    }
    for (String productID : productReactions.keySet()) {
      for (String reactionID : productReactions.get(productID)) {
        dotf.write("\t" + "\"r" + reactionID + "\"->" + "\"c" + productID + "\";\n");
        edges++;
      }
    }
    dotf.write("}");
    dotf.close();
  }


  /*
   * Example usage with PathBFS.
   * Look at PathBFS's getGraphTo to see how a ReactionsHypergraph is created.
   */
}
