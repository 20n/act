package com.act.reachables;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.MechanisticValidator;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CladeTraversal {

  public static final String OPTION_TARGET_INCHI = "i";
  public static final String OPTION_OUTPUT_INCHI_FILE_NAME = "o";
  public static final String OPTION_OUTPUT_REACTION_FILE_NAME = "r";
  public static final String OPTION_OUTPUT_FAILED_REACTIONS_DIR_NAME = "d";
  public static final String OPTION_ACT_DATA_FILE = "a";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class traverses the reachables tree from a target start point to find all chemicals derived from" +
          "the start point chemical."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_TARGET_INCHI)
        .argName("target inchi")
        .desc("The target inchi to start the clade search from.")
        .hasArg()
        .longOpt("target-inchi")
    );
    add(Option.builder(OPTION_OUTPUT_INCHI_FILE_NAME)
        .argName("inchi output file")
        .desc("A file containing a list of InChIs the correspond to the clade of the target molecule.")
        .hasArg()
        .longOpt("output-inchis")
    );
    add(Option.builder(OPTION_OUTPUT_REACTION_FILE_NAME)
        .argName("reaction output file")
        .desc("A file containing a list of reaction pathways corresponding to all paths from start point to a particular" +
            "reachable.")
        .hasArg()
        .longOpt("output-reactions")
    );
    add(Option.builder(OPTION_OUTPUT_FAILED_REACTIONS_DIR_NAME)
        .argName("failed reaction output directory")
        .desc("A directory containing reaction drawing of all the reaction that failed the mechanistic validator.")
        .hasArg()
        .longOpt("output-reaction-rendering")
    );
    add(Option.builder(OPTION_ACT_DATA_FILE)
        .argName("act data file")
        .desc("The act data file to read the reachables tree from.")
        .hasArg()
        .longOpt("act-data-file")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message.")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger(CladeTraversal.class);
  private static final NoSQLAPI db = new NoSQLAPI("marvin_v2", "marvin_v2");
  private ActData actData;
  private Map<Long, Set<Long>> parentToChildren = new HashMap<>();
  private MechanisticValidator validator;

  public CladeTraversal(MechanisticValidator validator, ActData actData) {
    this.actData = actData;
    this.validator = validator;
    this.constructParentToChildrenAssociations();
  }

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(CladeTraversal.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(CladeTraversal.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String targetInchi = cl.getOptionValue(OPTION_TARGET_INCHI, "InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)");
    String inchiFileName = cl.getOptionValue(OPTION_OUTPUT_INCHI_FILE_NAME, "Inchis.txt");
    String reactionsFileName = cl.getOptionValue(OPTION_OUTPUT_REACTION_FILE_NAME, "Reactions.txt");
    String reactionDirectory = cl.getOptionValue(OPTION_OUTPUT_FAILED_REACTIONS_DIR_NAME, "/");
    String actDataFile = cl.getOptionValue(OPTION_ACT_DATA_FILE, "result.actdata");

    MechanisticValidator validator = new MechanisticValidator(db);
    validator.init();

    ActData.instance().deserialize(actDataFile);
    CladeTraversal cladeTraversal = new CladeTraversal(validator, ActData.instance());
    Long idFromInchi = cladeTraversal.findNodeIdFromInchi(targetInchi);
    if (idFromInchi != null) {
      cladeTraversal.traverseTreeFromStartPoint(idFromInchi, inchiFileName, reactionsFileName, reactionDirectory);
    }
  }

  /**
   * This function constructs parent -> list of children associations of chemical ids based on the reachables tree.
   */
  private void constructParentToChildrenAssociations() {
    for (Map.Entry<Long, Long> childIdToParentId : this.actData.getActTree().parents.entrySet()) {
      Long parentId = childIdToParentId.getValue();
      Long childId = childIdToParentId.getKey();
      Set<Long> childIds = this.parentToChildren.get(parentId);
      if (childIds == null) {
        childIds = new HashSet<>();
      }
      childIds.add(childId);
      this.parentToChildren.put(parentId, childIds);
    }
  }

  /**
   * This function finds the node id from an inchi, which is useful since the reachable tree structure is referenced
   * by node ids.
   * @param inchi - The inchi to find the node id from.
   * @return The node id of the inchi.
   */
  private Long findNodeIdFromInchi(String inchi) {
    for (Map.Entry<Node, Long> nodeAndId : this.actData.getActTree().nodesAndIds().entrySet()) {
      Node node = nodeAndId.getKey();
      Long id = nodeAndId.getValue();
      Chemical chemical = db.readChemicalFromInKnowledgeGraph(id);
      if (chemical != null && chemical.getInChI().equals(inchi)) {
        return node.id;
      }
    }

    return null;
  }

  /**
   * This function traverses the reachables tree from the given start point using BFS, adds all the chemical's derivatives
   * to a file based on if they pass the mechanistic validator, and the derivatives' reaction pathway from the target
   * is also logged. Finally, for all the reactions that did not pass the mechanistic validator, we render those reactions
   * for furthur analysis into a directory.
   * @param startPointId - The start point node id to traverse from
   * @param validatedInchisFileName - The file containing all the derivative inchis that pass the validator.
   * @param reactionPathwayFileName - The file containing the reaction pathway information from source to target.
   * @param renderedReactionDirName - The directory containing all the rendered chemical reactions that failed the
   *                                mechanistic validator.
   * @throws IOException
   */
  private void traverseTreeFromStartPoint(Long startPointId, String validatedInchisFileName, String reactionPathwayFileName,
                                          String renderedReactionDirName) throws IOException {
    ReactionRenderer render = new ReactionRenderer(db.getReadDB());
    PrintWriter validatedInchisWriter = new PrintWriter(validatedInchisFileName, "UTF-8");
    PrintWriter reactionPathwayWriter = new PrintWriter(reactionPathwayFileName, "UTF-8");

    LinkedList<Long> queue = new LinkedList<>();
    queue.addAll(this.parentToChildren.get(startPointId));

    while (!queue.isEmpty()) {
      Long candidateId = queue.pop();
      validatedInchisWriter.println(db.readChemicalFromInKnowledgeGraph(candidateId).getInChI());
      reactionPathwayWriter.println(formatPathFromSrcToDst(startPointId, candidateId));

      Set<Long> children = this.parentToChildren.get(candidateId);
      if (children != null) {
        for (Long child : children) {
          for (Long rxnId : rxnIdsForEdge(candidateId, child)) {
            // Sometimes, the rxn id is negative, signifying a reaction happening in reverse. Flip the sign if this
            // happens.
            if (rxnId < 0) {
              rxnId = -1 * rxnId;
            }

            // Validate the reaction and only add its children to the queue if the reaction makes sense to our internal
            // ros.
            Map<Integer, List<Ero>> validatorResults = this.validator.validateOneReaction(rxnId);
            if (validatorResults != null && validatorResults.size() > 0) {
              queue.add(child);
            } else {
              try {
                render.drawAndSaveReaction(rxnId, renderedReactionDirName, true, "png", 1000, 1000);
              } catch (Exception e) {
                LOGGER.error("Error caught when trying to draw and save reaction %d with error message: %s", rxnId, e.getMessage());
              }
            }
          }
        }
      }
    }

    reactionPathwayWriter.close();
    validatedInchisWriter.close();
  }

  /**
   * The function creates a ordered list of chemicals from src to dst.
   * @param src - The src id
   * @param dst - The dst id
   * @return
   */
  public LinkedList<Long> pathFromSrcToDst(Long src, Long dst) {
    LinkedList<Long> result = new LinkedList<>();
    Long id = dst;
    result.add(id);

    while (!id.equals(src)) {
      Long newId = this.actData.getActTree().parents.get(id);
      result.add(newId);
      id = newId;
    }

    Collections.reverse(result);
    return result;
  }

  /**
   * This function finds all reactions that explain the given combination of src and dst chemicals.
   * @param src - The src node id.
   * @param dst - The dst node id.
   * @return
   */
  public Set<Long> rxnIdsForEdge(Long src, Long dst) {
    Set<Long> rxnsThatProduceChem = GlobalParams.USE_RXN_CLASSES ? ActData.instance().rxnClassesThatProduceChem.get(dst) :
        ActData.instance().rxnsThatProduceChem.get(dst);

    Set<Long> rxnsThatConsumeChem = GlobalParams.USE_RXN_CLASSES ? ActData.instance().rxnClassesThatConsumeChem.get(src) :
        ActData.instance().rxnsThatConsumeChem.get(src);

    Set<Long> intersection = new HashSet<>(rxnsThatProduceChem);
    intersection.retainAll(rxnsThatConsumeChem);

    return intersection;
  }

  /**
   * This function pretty prints a string that explains the reaction pathway from src to dst.
   * @param src - The src chemical
   * @param dst - The dst chemical
   * @return
   */
  public String formatPathFromSrcToDst(Long src, Long dst) {
    String result = "";
    List<Long> path = pathFromSrcToDst(src, dst);
    for (int i = 0; i < path.size() - 1; i++) {
      result += db.readChemicalFromInKnowledgeGraph(path.get(i)).getInChI();
      result += " --- ";
      Set<Long> rxnIds = rxnIdsForEdge(path.get(i), path.get(i + 1));
      result += StringUtils.join(rxnIds, ",");
      result += " ---> ";
    }
    result += db.readChemicalFromInKnowledgeGraph(path.get(path.size() - 1)).getInChI();
    return result;
  }
}
