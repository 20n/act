package com.act.reachables;

import act.shared.Reaction.RxnDataSource;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;

public class GlobalParams {

  static boolean LOG_PROGRESS = true;

	/* 
	 * TUNABLE PARAMETERS
	 */
  static boolean USE_RXN_CLASSES = true;
	static int _max_layers = 18; // cap at 18 layer
	static int _num_waves = 2;
	static int _startX = 40;
	static int _bufferX = 500;
	static int _bufferY = 50;
	static int _startY = 40;
	static int _width = 500;
	static int _height = 8000;
	static int _catch_all_height = 2000;
	static int _catch_all_span = 10; // 10 times _width + _bufferX
	static boolean _do_inertial_waves = false; // the waves avg both fwd and bwd's so that the layout looks more organic
	static int _inertial_wave_periphery_nudge = 800;
	static boolean _do_clustered_unreachables = true;
	static boolean _do_render_chemicals = false;
	static int _actTreeSignificantFanout = 10; // 15 will give 28, 10 will give 58 important ancestors
	static int _actTreePickNameOfLengthAbout = 20; // 15 characters
	static int _actTreeMinimumSizeOfConditionalTree = 1; // the "assumed" chemical, has to enable at least 10 new reachables 
	static boolean _actTreeCreateHostCentricMap = false;
	static boolean _actTreeDumpClades = false;
	static boolean _actTreeCreateUnreachableTrees = false;
	static boolean _actTreeIncludeAssumedReachables = true;
	static int _actTreeCompressNodesWithChildrenLessThan = 0;

  static boolean _actTreeIgnoreReactionsWithNoSubstrates = true;
  static boolean _actTreeOnlyIncludeRxnsWithSequences = true;

  private static RxnDataSource[] _includedRxnSources = { RxnDataSource.METACYC, RxnDataSource.BRENDA };
  static List<RxnDataSource> _ReachablesIncludeRxnSources = Arrays.asList(_includedRxnSources);
	
	static String[] _PricesFrom = { "SIGMA", "DRUGBANK" };
	static int _GetPricesFrom = 0; // "SIGMA";
	
	static String[] _ROTypes = { "ERO", "CRO" };

	// when expanding, use CROs or EROs
	static int _ROExpandUsing = 0; // "ERO";
	
	// when creating EC vs RO intersection network, what types of ROs do the green nodes correspond to?
	static int _ECvsWhatROType = 1; // "CRO";
	
	// the threshold on the number of parent reactions coming in
	// if a node has more than this number of parent reactions coming in
	// it is not shown.
	static int _parent_incoming_threshold_paths_svg = 10;
	
	// conditional reachability properties.. we do not export; only the first two really need to be exported to the front end (if at all)
	static int _topKClusters = 40; // 4000 is a good number; these are clusters of conditional reachability
	static int _limitedPreconditionsConsidered = 0; // set to 0 if you want full precondition calculation, or num>0 for num preconditions
	static double _clusterUniquenessThreshold = 2.0; // still owns twice as many nodes as were stolen 
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
		layoutProperties.add(new Tunable("_actTreeSignificantFanout", "Tree Layout: How many children for it to be an important?", Tunable.INTEGER, new Integer(_actTreeSignificantFanout)));
		layoutProperties.add(new Tunable("_actTreeMinimumSizeOfConditionalTree", "Tree Layout: For unreachables, ignore trees with enabled reachables less than this:", Tunable.INTEGER, new Integer(_actTreeMinimumSizeOfConditionalTree)));
		layoutProperties.add(new Tunable("_actTreePickNameOfLengthAbout", "Tree Layout: Node names: How many characters long?", Tunable.INTEGER, new Integer(_actTreePickNameOfLengthAbout)));
		layoutProperties.add(new Tunable("_actTreeCompressNodesWithChildrenLessThan", "Tree Layout: Compress skinny paths less than: ", Tunable.INTEGER, new Integer(_actTreeCompressNodesWithChildrenLessThan)));
		layoutProperties.add(new Tunable("_actTreeCreateHostCentricMap", "Tree Layout: Host Chassis Centric?", Tunable.BOOLEAN, new Boolean(_actTreeCreateHostCentricMap)));
		layoutProperties.add(new Tunable("_actTreeCreateUnreachableTrees", "Tree Layout: Create unreachable trees?", Tunable.BOOLEAN, new Boolean(_actTreeCreateUnreachableTrees)));
		layoutProperties.add(new Tunable("_actTreeIncludeAssumedReachables", "Tree Layout: In reachables, include manually assumed precursors?", Tunable.BOOLEAN, new Boolean(_actTreeIncludeAssumedReachables)));
		layoutProperties.add(new Tunable("_actTreeDumpClades", "Tree Layout: Dump clades to /Applications/Cytoscape/output.log?", Tunable.BOOLEAN, new Boolean(_actTreeDumpClades)));
		layoutProperties.add(new Tunable("_actTreePricesFrom", "Tree Layout: Pull prices from:", Tunable.LIST, new Integer(_GetPricesFrom), (Object)_PricesFrom, null, 0));
		
		layoutProperties.add(new Tunable("_do_render_chemicals", "In HTML Dump, Render Chemicals", Tunable.BOOLEAN, new Boolean(_do_render_chemicals)));
		layoutProperties.add(new Tunable("_do_clustered_unreachables", "Cluster Unreachables", Tunable.BOOLEAN, new Boolean(_do_clustered_unreachables)));
		layoutProperties.add(new Tunable("_do_inertial_waves", "Inertial waves", Tunable.BOOLEAN, new Boolean(_do_inertial_waves)));
		layoutProperties.add(new Tunable("#waves", "# Y Normalization Waves", Tunable.INTEGER, new Integer(_num_waves)));
		layoutProperties.add(new Tunable("_startX", "Left Margin", Tunable.INTEGER, new Integer(_startX)));
		layoutProperties.add(new Tunable("_bufferX", "Horizontal Padding", Tunable.INTEGER, new Integer(_bufferX)));
		layoutProperties.add(new Tunable("_startY", "Bottom Margin", Tunable.INTEGER, new Integer(_startY)));
		layoutProperties.add(new Tunable("_width", "Width of Layer's Box", Tunable.INTEGER, new Integer(_width)));
		layoutProperties.add(new Tunable("_height", "Height of Layer's Box", Tunable.INTEGER, new Integer(_height)));
		layoutProperties.add(new Tunable("_parent_incoming_threshold_paths_svg", "Max Incoming Rxns in Pathway SVG", Tunable.INTEGER, new Integer(_parent_incoming_threshold_paths_svg)));
		
		layoutProperties.add(new Tunable("_topKClusters", "Conditional Reachability: Show Top-K; K=", Tunable.INTEGER, new Integer(_topKClusters)));
		layoutProperties.add(new Tunable("_limitedPreconditionsConsidered", "Conditional Reachability: Evaluate K preconditions; K= (0 for all)", Tunable.INTEGER, new Integer(_limitedPreconditionsConsidered)));
		//[>600 no effect (=3376 reach), 500 (=4225 reach), 400 (=4393), <400 starts including true-unreachables]
		layoutProperties.add(new Tunable("_ifEnabledClusterSizeThenAssumeNative", "Unreach clusters: >k default endogenous (550: assumes fatty acid) ", Tunable.INTEGER, new Integer(_ifEnabledClusterSizeThenAssumeNative)));
		
		layoutProperties.add(new Tunable("_hostOrganisms", "Host organisms", Tunable.LIST, new Integer(0), (Object)_hostOrganisms, null, 0));
		layoutProperties.add(new Tunable("_ROExpandUsing", "RO Expand Using:", Tunable.LIST, new Integer(_ROExpandUsing), (Object)_ROTypes, null, 0));
		layoutProperties.add(new Tunable("_ECvsWhatROType", "EC Comparison using Act RO type:", Tunable.LIST, new Integer(_ECvsWhatROType), (Object)_ROTypes, null, 0));
		
		layoutProperties.initializeProperties();
		updateSettings(true);
	}

	public void updateSettings(boolean force) {
		layoutProperties.updateValues();
		Tunable t;

		t = layoutProperties.get("_actTreeSignificantFanout");
		if ((t != null) && (t.valueChanged() || force))
			_actTreeSignificantFanout = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_actTreeMinimumSizeOfConditionalTree");
		if ((t != null) && (t.valueChanged() || force))
			_actTreeMinimumSizeOfConditionalTree = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_actTreeCompressNodesWithChildrenLessThan");
		if ((t != null) && (t.valueChanged() || force))
			_actTreeCompressNodesWithChildrenLessThan = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_actTreePickNameOfLengthAbout");
		if ((t != null) && (t.valueChanged() || force))
			_actTreePickNameOfLengthAbout = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_actTreeCreateHostCentricMap");
		if ((t != null) && (t.valueChanged() || force))
			_actTreeCreateHostCentricMap = ((Boolean) t.getValue()).booleanValue();
		t = layoutProperties.get("_actTreeCreateUnreachableTrees");
		if ((t != null) && (t.valueChanged() || force))
			_actTreeCreateUnreachableTrees = ((Boolean) t.getValue()).booleanValue();
		t = layoutProperties.get("_actTreeIncludeAssumedReachables");
		if ((t != null) && (t.valueChanged() || force))
			_actTreeIncludeAssumedReachables = ((Boolean) t.getValue()).booleanValue();
		t = layoutProperties.get("_actTreeDumpClades");
		if ((t != null) && (t.valueChanged() || force))
			_actTreeDumpClades = ((Boolean) t.getValue()).booleanValue();
		t = layoutProperties.get("_do_render_chemicals");
		if ((t != null) && (t.valueChanged() || force))
			_do_render_chemicals = ((Boolean) t.getValue()).booleanValue();
		t = layoutProperties.get("_parent_incoming_threshold_paths_svg");
		if ((t != null) && (t.valueChanged() || force))
			_parent_incoming_threshold_paths_svg = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_do_clustered_unreachables");
		if ((t != null) && (t.valueChanged() || force))
			_do_clustered_unreachables = ((Boolean) t.getValue()).booleanValue();
		t = layoutProperties.get("_do_inertial_waves");
		if ((t != null) && (t.valueChanged() || force))
			_do_inertial_waves = ((Boolean) t.getValue()).booleanValue();
		t = layoutProperties.get("#waves");
		if ((t != null) && (t.valueChanged() || force))
			_num_waves = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_startX");
		if ((t != null) && (t.valueChanged() || force))
			_startX = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_startY");
		if ((t != null) && (t.valueChanged() || force))
			_startY = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_bufferX");
		if ((t != null) && (t.valueChanged() || force))
			_bufferX = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_width");
		if ((t != null) && (t.valueChanged() || force))
			_width = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_height");
		if ((t != null) && (t.valueChanged() || force))
			_height = ((Integer) t.getValue()).intValue();

		t = layoutProperties.get("_topKClusters");
		if ((t != null) && (t.valueChanged() || force))
			_topKClusters = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_limitedPreconditionsConsidered");
		if ((t != null) && (t.valueChanged() || force))
			_limitedPreconditionsConsidered = ((Integer) t.getValue()).intValue();
		t = layoutProperties.get("_ifEnabledClusterSizeThenAssumeNative");
		if ((t != null) && (t.valueChanged() || force))
			_ifEnabledClusterSizeThenAssumeNative = ((Integer) t.getValue()).intValue();

		t = layoutProperties.get("_hostOrganisms");
		if ((t != null) && (t.valueChanged() || force)) {
			_hostOrganismIndex = ((Integer) t.getValue()).intValue();
		}
		t = layoutProperties.get("_ROExpandUsing");
		if ((t != null) && (t.valueChanged() || force)) {
			_ROExpandUsing = ((Integer) t.getValue()).intValue();
		}
		t = layoutProperties.get("_ECvsWhatROType");
		if ((t != null) && (t.valueChanged() || force)) {
			_ECvsWhatROType = ((Integer) t.getValue()).intValue();
		}
		t = layoutProperties.get("_actTreePricesFrom");
		if ((t != null) && (t.valueChanged() || force)) {
			_GetPricesFrom = ((Integer) t.getValue()).intValue();
		}
	}
	
	/*
	 * Layout properties and Layout Init
	 */
	private LayoutProperties layoutProperties;

	public GlobalParams() {
		super();
		layoutProperties = new LayoutProperties(getName());
		initialize_properties();
	}

	public void updateSettings() {
		updateSettings(false);
	}

	// getName is used to construct property strings.
	public String getName() {
		return "Act Layout";
	}

	// toString is used to get the user-visible name
	public String toString() {
		return "Act Layout";
	}

	public void revertSettings() {
		layoutProperties.revertProperties();
	}

	public LayoutProperties getSettings() {
		return layoutProperties;
	}
	
}

class LayoutProperties {
  HashMap<String, Tunable> tunables;
  String paramset_id;

  LayoutProperties(String name) {
    this.paramset_id = name;
    this.tunables = new HashMap<String, Tunable>();
  }

  void add(Tunable t) {
    this.tunables.put(t.key, t);
  }
  
  Tunable get(String tid) {
    return this.tunables.get(tid);
  }

  void initializeProperties() {
  }

  void updateValues() {
  }

  void revertProperties() {
  }
}

class Tunable {
  public static final int INTEGER = 0;
  public static final int BOOLEAN = 1;
  public static final int LIST = 2;

  String key;
  Object val;
  String desc;
  int typ;
  public Tunable(String prop, String desc, int t, Object var_holding_val) {
    this.key = prop;
    this.val = var_holding_val;
    this.desc = desc;
    this.typ = t;
  }

  // Constructor for lists is different
  Object[] opts;
  Object picked_opt;
  public Tunable(String prop, String desc, int t, Object var_holding_val, Object array_of_opts, Object ignoredparam, int default_opt) {
    if (t != Tunable.LIST) {
      System.err.println("Abort. Tunables.LIST constructor called, but not on lists.");
      System.exit(-1);
    }
    this.key = prop;
    this.val = var_holding_val;
    this.desc = desc;
    this.typ = t;

    this.opts = (Object[])array_of_opts;
    this.picked_opt = this.opts[default_opt];
  }

  Boolean valueChanged() {
    // this would be if we had setting written to some file, etc
    // and there was a static method in Tunable that was watching
    // for changes. And if it a key, val pair changed would update 
    // that particular tunable from a static map of all tunables
    return false;
  }

  Object getValue() {
    return this.val;
  }
}
