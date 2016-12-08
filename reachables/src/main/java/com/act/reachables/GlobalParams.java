package com.act.reachables;

import act.shared.Reaction.RxnDataSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class GlobalParams {

  // LOG_PROGRESS says the computation should
  // output verbose messages while process dataset
  public static boolean LOG_PROGRESS = true;

  // MAX_CASCADE_DEPTH parametrizes how far many steps
  // back in the acyclic graph do we dump to the output
  // DOT of the cascade. This is measured in # chems
  // of path backwards from the specific reachable
  public static int MAX_CASCADE_DEPTH = 5;
  // MAX_CASCADE_UPFANOUT parametrizes how many reactions
  // upwards from each node are allowed. If more than
  // those exist then a fake rxn node with a msg is shown
  public static int MAX_CASCADE_UPFANOUT = 10;
  // and when the # rxns > MAX_CASCADE_UPFANOUT we dump
  // out a fake reaction, that is annotated as such
  // the ID for this fake reaction in the
  // (FAKE_RXN_ID + #omitted)
  public static long FAKE_RXN_ID = 999999999L;
  // do we write the waterfalls to the DB?
  public static boolean _writeWaterfallsToDB = false;

  /*
   * TUNABLE PARAMETERS
   */
  public static boolean USE_RXN_CLASSES = false;
  private static int _max_layers = 18; // cap at 18 layer
  private static int _num_waves = 2;
  private static int _startX = 40;
  private static int _bufferX = 500;
  private static int _bufferY = 50;
  private static int _startY = 40;
  private static int _width = 500;
  private static int _height = 8000;
  private static int _catch_all_height = 2000;
  private static int _catch_all_span = 10; // 10 times _width + _bufferX
  private static boolean _do_inertial_waves = false; // the waves avg both fwd and bwd's so that the layout looks more organic
  private static int _inertial_wave_periphery_nudge = 800;
  private static boolean _do_clustered_unreachables = true;
  private static boolean _do_render_chemicals = false;
  public static int actTreeSignificantFanout = 10; // 15 will give 28, 10 will give 58 important ancestors
  public static int actTreePickNameOfLengthAbout = 20; // 15 characters
  public static int actTreeMinimumSizeOfConditionalTree = 1; // the "assumed" chemical, has to enable at least 10 new reachables
  public static boolean actTreeCreateHostCentricMap = false;
  public static boolean actTreeDumpClades = false;
  public static boolean actTreeCreateUnreachableTrees = false;
  public static int actTreeCompressNodesWithChildrenLessThan = 0;

  public static boolean _actTreeIgnoreReactionsWithNoSubstrates = true;
  public static boolean _actTreeOnlyIncludeRxnsWithSequences = true;

  private static RxnDataSource[] _includedRxnSources = { RxnDataSource.METACYC, RxnDataSource.BRENDA };
  static List<RxnDataSource> _ReachablesIncludeRxnSources = Arrays.asList(_includedRxnSources);

  static String[] _PricesFrom = { "SIGMA", "DRUGBANK" };
  static int _GetPricesFrom = 0; // "SIGMA";

  static String[] _ROTypes = { "ERO", "CRO" };

  // when expanding, use CROs or EROs
  static int _ROExpandUsing = 0; // "ERO";

  // when creating EC vs RO intersection network, what types of ROs do the green nodeMapping correspond to?
  static int _ECvsWhatROType = 1; // "CRO";

  // the threshold on the number of parent reactions coming in
  // if a node has more than this number of parent reactions coming in
  // it is not shown.
  static int _parent_incoming_threshold_paths_svg = 10;

  // conditional reachability properties.. we do not export; only the first two really need to be exported to the front end (if at all)
  static int _topKClusters = 40; // 4000 is a good number; these are clusters of conditional reachability
  static int _limitedPreconditionsConsidered = 0; // set to 0 if you want full precondition calculation, or num>0 for num preconditions
  static double _clusterUniquenessThreshold = 2.0; // still owns twice as many nodeMapping as were stolen
  static boolean _showReachablesNode = false; // show the highly connect node for the reachables cluster

  static String[] _hostOrganisms = {
    "Escherichia coli" /* org_id = 562 */,
    "Saccharomyces cerevisiae" /* org_id = 4932 */
  };
  // static String _host = _hostOrganisms[0];
  static Long[] _hostOrganismIDs = null; // initialized after LoadAct
  private static int _hostOrganismIndex = 0;
  // and db.organismnames.find({org_id:562}) = 17 entries
  // and db.actfamilies.find({"organisms.id":562}) = 3359 entries

  public static Long gethostOrganismID() {
    return _hostOrganismIDs[_hostOrganismIndex];
  }
  public static String inROExpandWhichROType() {
    return _ROTypes[_ROExpandUsing];
  }
  public static String inECvsROwhichROType() {
    return _ROTypes[_ECvsWhatROType];
  }
  public static String pullPricesFrom() {
    return _PricesFrom[_GetPricesFrom];
  }

  static int _ifEnabledClusterSizeThenAssumeNative = 500;

  // For ero operations, we need rendering etc facilities
  // we access render.sh, render_query.sh, applyRO.sh, applyROfile.sh (and mongoactsynth.jar)
  // static String helperFnsDir = "/Users/saurabhs/saurabhs-msr/src/Act/Installer/";
  static String helperFnsDir = "/Applications/Cytoscape_v2.8.3/ActHelpers/";

  protected void initialize_properties() {
    paramsInt.add(new Tunable("actTreeSignificantFanout", "Tree Layout: How many children for it to be an important?", new Integer(actTreeSignificantFanout)));
    paramsInt.add(new Tunable("actTreeMinimumSizeOfConditionalTree", "Tree Layout: For unreachables, ignore trees with enabled reachables less than this:", new Integer(actTreeMinimumSizeOfConditionalTree)));
    paramsInt.add(new Tunable("actTreePickNameOfLengthAbout", "Tree Layout: Node names: How many characters long?", new Integer(actTreePickNameOfLengthAbout)));
    paramsInt.add(new Tunable("actTreeCompressNodesWithChildrenLessThan", "Tree Layout: Compress skinny paths less than: ", new Integer(actTreeCompressNodesWithChildrenLessThan)));
    paramsBool.add(new Tunable("actTreeCreateHostCentricMap", "Tree Layout: Host Chassis Centric?", new Boolean(actTreeCreateHostCentricMap)));
    paramsBool.add(new Tunable("actTreeCreateUnreachableTrees", "Tree Layout: Create unreachable trees?", new Boolean(actTreeCreateUnreachableTrees)));
    paramsBool.add(new Tunable("actTreeDumpClades", "Tree Layout: Dump clades to /Applications/Cytoscape/output.log?", new Boolean(actTreeDumpClades)));

    paramsBool.add(new Tunable("_do_render_chemicals", "In HTML Dump, Render Chemicals", new Boolean(_do_render_chemicals)));
    paramsBool.add(new Tunable("_do_clustered_unreachables", "Cluster Unreachables", new Boolean(_do_clustered_unreachables)));
    paramsBool.add(new Tunable("_do_inertial_waves", "Inertial waves", new Boolean(_do_inertial_waves)));
    paramsInt.add(new Tunable("#waves", "# Y Normalization Waves", new Integer(_num_waves)));
    paramsInt.add(new Tunable("_startX", "Left Margin", new Integer(_startX)));
    paramsInt.add(new Tunable("_bufferX", "Horizontal Padding", new Integer(_bufferX)));
    paramsInt.add(new Tunable("_startY", "Bottom Margin", new Integer(_startY)));
    paramsInt.add(new Tunable("_width", "Width of Layer's Box", new Integer(_width)));
    paramsInt.add(new Tunable("_height", "Height of Layer's Box", new Integer(_height)));
    paramsInt.add(new Tunable("_parent_incoming_threshold_paths_svg", "Max Incoming Rxns in Pathway SVG", new Integer(_parent_incoming_threshold_paths_svg)));

    paramsInt.add(new Tunable("_topKClusters", "Conditional Reachability: Show Top-K; K=", new Integer(_topKClusters)));
    paramsInt.add(new Tunable("_limitedPreconditionsConsidered", "Conditional Reachability: Evaluate K preconditions; K= (0 for all)", new Integer(_limitedPreconditionsConsidered)));
    //[>600 no effect (=3376 reach), 500 (=4225 reach), 400 (=4393), <400 starts including true-unreachables]
    paramsInt.add(new Tunable("_ifEnabledClusterSizeThenAssumeNative", "Unreach clusters: >k default endogenous (550: assumes fatty acid) ", new Integer(_ifEnabledClusterSizeThenAssumeNative)));

    paramsList.add(new Tunable("_actTreePricesFrom", "Tree Layout: Pull prices from:", new Integer(_GetPricesFrom), _PricesFrom, 0));
    paramsList.add(new Tunable("_hostOrganisms", "Host organisms", new Integer(0), _hostOrganisms, 0));
    paramsList.add(new Tunable("_ROExpandUsing", "RO Expand Using:", new Integer(_ROExpandUsing), _ROTypes, 0));
    paramsList.add(new Tunable("_ECvsWhatROType", "EC Comparison using Act RO type:", new Integer(_ECvsWhatROType), _ROTypes, 0));

    updateSettings(true);
  }

  public void updateSettings(boolean force) {
    Tunable<Integer> ti;
    Tunable<String[]> tl;
    Tunable<Boolean> tb;

    ti = paramsInt.get("actTreeSignificantFanout");
    if ((ti != null) && (ti.valueChanged() || force))
      actTreeSignificantFanout = ti.getValue().intValue();
    ti = paramsInt.get("actTreeMinimumSizeOfConditionalTree");
    if ((ti != null) && (ti.valueChanged() || force))
      actTreeMinimumSizeOfConditionalTree = ti.getValue().intValue();
    ti = paramsInt.get("actTreeCompressNodesWithChildrenLessThan");
    if ((ti != null) && (ti.valueChanged() || force))
      actTreeCompressNodesWithChildrenLessThan = ti.getValue().intValue();
    ti = paramsInt.get("actTreePickNameOfLengthAbout");
    if ((ti != null) && (ti.valueChanged() || force))
      actTreePickNameOfLengthAbout = ti.getValue().intValue();
    tb = paramsBool.get("actTreeCreateHostCentricMap");
    if ((tb != null) && (tb.valueChanged() || force))
      actTreeCreateHostCentricMap = tb.getValue().booleanValue();
    tb = paramsBool.get("actTreeCreateUnreachableTrees");
    if ((tb != null) && (tb.valueChanged() || force))
      actTreeCreateUnreachableTrees = tb.getValue().booleanValue();
    tb = paramsBool.get("actTreeDumpClades");
    if ((tb != null) && (tb.valueChanged() || force))
      actTreeDumpClades = tb.getValue().booleanValue();
    tb = paramsBool.get("_do_render_chemicals");
    if ((tb != null) && (tb.valueChanged() || force))
      _do_render_chemicals = tb.getValue().booleanValue();
    ti = paramsInt.get("_parent_incoming_threshold_paths_svg");
    if ((ti != null) && (ti.valueChanged() || force))
      _parent_incoming_threshold_paths_svg = ti.getValue().intValue();
    tb = paramsBool.get("_do_clustered_unreachables");
    if ((tb != null) && (tb.valueChanged() || force))
      _do_clustered_unreachables = tb.getValue().booleanValue();
    tb = paramsBool.get("_do_inertial_waves");
    if ((tb != null) && (tb.valueChanged() || force))
      _do_inertial_waves = tb.getValue().booleanValue();
    ti = paramsInt.get("#waves");
    if ((ti != null) && (ti.valueChanged() || force))
      _num_waves = ti.getValue().intValue();
    ti = paramsInt.get("_startX");
    if ((ti != null) && (ti.valueChanged() || force))
      _startX = ti.getValue().intValue();
    ti = paramsInt.get("_startY");
    if ((ti != null) && (ti.valueChanged() || force))
      _startY = ti.getValue().intValue();
    ti = paramsInt.get("_bufferX");
    if ((ti != null) && (ti.valueChanged() || force))
      _bufferX = ti.getValue().intValue();
    ti = paramsInt.get("_width");
    if ((ti != null) && (ti.valueChanged() || force))
      _width = ti.getValue().intValue();
    ti = paramsInt.get("_height");
    if ((ti != null) && (ti.valueChanged() || force))
      _height = ti.getValue().intValue();

    ti = paramsInt.get("_topKClusters");
    if ((ti != null) && (ti.valueChanged() || force))
      _topKClusters = ti.getValue().intValue();
    ti = paramsInt.get("_limitedPreconditionsConsidered");
    if ((ti != null) && (ti.valueChanged() || force))
      _limitedPreconditionsConsidered = ti.getValue().intValue();
    ti = paramsInt.get("_ifEnabledClusterSizeThenAssumeNative");
    if ((ti != null) && (ti.valueChanged() || force))
      _ifEnabledClusterSizeThenAssumeNative = ti.getValue().intValue();

    tl = paramsList.get("_hostOrganisms");
    if ((tl != null) && (tl.valueChanged() || force)) {
      _hostOrganismIndex = ti.getValue().intValue();
    }
    tl = paramsList.get("_ROExpandUsing");
    if ((tl != null) && (tl.valueChanged() || force)) {
      _ROExpandUsing = ti.getValue().intValue();
    }
    tl = paramsList.get("_ECvsWhatROType");
    if ((tl != null) && (tl.valueChanged() || force)) {
      _ECvsWhatROType = ti.getValue().intValue();
    }
    tl = paramsList.get("_actTreePricesFrom");
    if ((tl != null) && (tl.valueChanged() || force)) {
      _GetPricesFrom = ti.getValue().intValue();
    }
  }

  /*
   * Layout properties and Layout Init
   */
  private LayoutProperties<String[]> paramsList;
  private LayoutProperties<Integer> paramsInt;
  private LayoutProperties<Boolean> paramsBool;

  public GlobalParams() {
    super();
    this.paramsList = new LayoutProperties<String[]>();
    this.paramsBool = new LayoutProperties<Boolean>();
    this.paramsInt = new LayoutProperties<Integer>();
    initialize_properties();
  }

  public void updateSettings() {
    updateSettings(false);
  }

  public static class LayoutProperties<T> {
    HashMap<String, Tunable<T>> tunables;

    LayoutProperties() {
      this.tunables = new HashMap<String, Tunable<T>>();
    }

    void add(Tunable<T> t) {
      this.tunables.put(t.key, t);
    }

    Tunable<T> get(String tid) {
      return this.tunables.get(tid);
    }
  }

  public static class Tunable<T> {
    String key;
    T val;
    String desc;
    int typ;
    public Tunable(String prop, String desc, T var_holding_val) {
      this.key = prop;
      this.val = var_holding_val;
      this.desc = desc;
    }

    T[] opts;
    T picked_opt;
    public Tunable(String prop, String desc, T var_holding_val, T[] array_of_opts, int default_opt) {
      this.key = prop;
      this.val = var_holding_val;
      this.desc = desc;

      this.opts = (T[])array_of_opts;
      this.picked_opt = this.opts[default_opt];
    }

    Boolean valueChanged() {
      // this would be if we had setting written to some file, etc
      // and there was a static method in Tunable that was watching
      // for changes. And if it a key, val pair changed would update
      // that particular tunable from a static map of all tunables
      return false;
    }

    T getValue() {
      return this.val;
    }
  }
}

