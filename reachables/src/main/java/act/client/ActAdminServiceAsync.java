package act.client;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.Organism;
import act.shared.Parameters;
import act.shared.Path;
import act.shared.Reaction;
import act.shared.ReactionDetailed;

import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * The async counterpart of <code>GreetingService</code>.
 */
public interface ActAdminServiceAsync {
  void populateActEnzymesFromSQL(String mongoActHost, int mongoActPort, String mongoActDB,
      String sqlHost, long reactionTableBlockSize, long dontGoPastUUID, AsyncCallback<String> callback)
      throws IllegalArgumentException;

  void findPathway(String mongoActHost, int mongoActPort, String mongoActDB,
      String optionalSource, String target, List<String> targetSMILES, List<String> targetCommonNames,
      int numSimilar, int numOps, int maxNewChems, int numPaths, int augmentWithROSteps, String augmentedNetworkName,
      boolean addNativeSrcs, boolean findConcrete, boolean useFnGrpAbs, Set<Long> ignoredChemicals, boolean weighted,
      AsyncCallback<List<Path>> asyncCallback);

  void findAbstractPathway(String mongoActHost, int mongoActPort,
      String mongoActDB, List<String> targetSMILES_abs,
      List<String> targetCommonNames_abs, int numOps,
      String rxns_list_file, AsyncCallback<Void> callback);

  void augmentNetwork(String mongoActHost, int mongoActPort,
      String mongoActDB, int numOps, int augmentWithROSteps,
      String augmentedNwName, String rxns_list_file,
      AsyncCallback<Integer> asyncCallback);

  void execAnalyticsScript(String mongoActHost, int mongoActPort, String mongoActDB,
      Parameters.AnalyticsScripts script, AsyncCallback<List<ReactionDetailed>> asyncCallback) throws IllegalArgumentException;

  void dumpAct2File(String actHost, int actPort, String actDB,
      String outputFile, AsyncCallback<String> asyncCallback) throws IllegalArgumentException;

  void diffReactions(String actHost, int actPort, String actDB,
      Long lowUUID, Long highUUID, String rxns_list_file, boolean addToDB, AsyncCallback<String> asyncCallback) throws IllegalArgumentException;

  void canonicalizeName(String actHost, int actPort, String actDB,
      String synonym, AsyncCallback<List<Chemical>> asyncCallback) throws IllegalArgumentException;

  void canonicalizeAll(String actHost, int actPort, String actDB,
      List<String> commonNames, AsyncCallback<HashMap<String, List<Chemical>>> asyncCallback);

  void serverInitConfig(String configfile, boolean quiet, AsyncCallback<Configuration> asyncCallback);

  void lookupOrganism(String actHost, int actPort, String actDB,
      String organism, AsyncCallback<List<Organism>> asyncCallback);

  void getCommonPaths(int k, AsyncCallback<List<Path>> asyncCallback);
}
