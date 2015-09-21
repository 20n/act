package act.client;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.Organism;
import act.shared.Parameters;
import act.shared.Parameters.AnalyticsScripts;
import act.shared.Path;
import act.shared.Reaction;
import act.shared.ReactionDetailed;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * The client side stub for the RPC service.
 */
@RemoteServiceRelativePath("greet")
public interface ActAdminService extends RemoteService {
  String populateActEnzymesFromSQL(String mongoActHost, int mongoActPort, String mongoActDB,
      String sqlHost, long reactionTableBlockSize, long dontGoPastUUID) throws IllegalArgumentException;

  List<Path> findPathway(String mongoActHost, int mongoActPort,
      String mongoActDB, String optionalSource, String target, List<String> targetSMILES, List<String> targetCommonNames,
      int numSimilar, int numOps, int maxNewChems, int numPaths, int augmentWithROSteps, String augmentedNetworkName,
      boolean addNativeSrcs, boolean findConcrete, boolean useFnGrpAbs, Set<Long> ignoredChemicals, boolean weighted);

  void findAbstractPathway(String mongoActHost, int mongoActPort, String mongoActDB,
      List<String> targetSMILES_abs, List<String> targetCommonNames_abs, int numOps, String rxns_list_file);

  Integer augmentNetwork(String mongoActHost, int mongoActPort,
      String mongoActDB, int numOps, int augmentWithROSteps,
      String augmentedNwName, String rxns_list_file);

  List<ReactionDetailed> execAnalyticsScript(String mongoActHost, int mongoActPort, String mongoActDB,
      AnalyticsScripts script) throws IllegalArgumentException;

  String dumpAct2File(String actHost, int actPort, String actDB,
      String outputFile) throws IllegalArgumentException;

  String diffReactions(String actHost, int actPort, String actDB,
      Long lowUUID, Long highUUID, String rxns_list_file, boolean addToDB);

  List<Chemical> canonicalizeName(String actHost, int actPort, String actDB,
      String synonym) throws IllegalArgumentException;

  HashMap<String, List<Chemical>> canonicalizeAll(String actHost, int actPort, String actDB,
      List<String> commonNames) throws IllegalArgumentException;

  Configuration serverInitConfig(String configfile, boolean quiet);

  List<Organism> lookupOrganism(String actHost, int actPort, String actDB,
      String organism);

  List<Path> getCommonPaths(int k);
}
