package act.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import act.render.RenderTopROJobs;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import act.server.ActAdminServiceImpl;
import act.server.Canonicalizer;
import act.server.Molecules.ERO;
import act.server.Molecules.RO;
import act.server.Molecules.RxnWithWildCards;
import act.server.Molecules.SMILES;
import act.server.Molecules.RxnTx;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.Configuration.ReactionSimplification;
import act.shared.Reaction;
import act.shared.helpers.P;
import act.shared.helpers.XMLToImportantChemicals;

/* 
 * THIS IS OUTSIDE THE GWT FRAMEWORK...
 * For installation etc, there are server operations that are best run from the command line...
 */
public class CommandLineRun {
	enum Command { 
		INFER_OPS, DUMP_OPS, DUMP_TOP_ROS, 
		AUGMENT_NW, APPLY_RO,
		FIND_PATH, FIND_ABSTRACT_PATH,
		CHECK_COFACTORS, COFACTOR_AAMs, SIMILARITY,
		RENDER, CHEMSPELL, XML2IMPCHEM,
		INCHI2SMILES, CONSISTENT_INCHI, SMARTS_SEARCH,
		RUN_GAME_SERVER };

	static CommandLine cmdline;
	static boolean quiet;
	
	public static void main(String[] args) throws Exception {
		
		Options opts = createOptions();
		parseAndSetCmdLine(args, opts);
		quiet = readBoolean("quiet");
		if (!quiet)
			printHelp(args[0], opts);

		String actHost = read("host", "localhost"); 
		int actPort = Integer.parseInt(read("port", "27017")); 
		String actDB = read("dbs", "actv01"); 
		Long lowUUID = Long.parseLong(read("start", "0")); 
		Long highUUID = Long.parseLong(read("end", "60000")); 
		int maxNewChems = Integer.parseInt(read("maxchems", "10000"));
		int numOps = Integer.parseInt(read("numops", "10"));
		int numPaths = Integer.parseInt(read("numpaths", "5"));
		int numSimilar = Integer.parseInt(read("numsimilar", "3")); // if searching for concrete using smiles, but to similar compounds
		int augmentWithROs = Integer.parseInt(read("rosteps_dfs", "-1"));
		int roRep = Integer.parseInt(read("ro_representative", "-1"));
		boolean noAddToDB = readBoolean("noAdd2db");
		boolean findConcrete = readBoolean("noROs");
		boolean addNativeSources = readBoolean("use_natives");
		boolean useFnGrpAbstraction = readBoolean("useFnGrpAbs");
		String target = read("target", null);
		String comment = read("comment", null);
		String targetsFile = read("targetsFile", null);
		String substratesFile = read("substratesFile", null);
		String roFile = read("rosFile", null);
		String targetIDs = read("targetIDs", null);
		String optsource = read("source", null);
		String outDir = read("outputdir", null);
		String cofactor_pair_file = read("cofactor_pair_file", null);
		String roType = read("rotype", null);
		String simType = read("simtype", null);
		Command cmd = Command.valueOf(read("exec", null));
		String config_file = read("config", null);
		String rxns_white_list_file = read("rxns_whitelist", null);
		String augmentedNetworkName = read("augmentednw", null);

		ActAdminServiceImpl actServer = new ActAdminServiceImpl(true /* dont start game server */);
		actServer.serverInitConfig(config_file, quiet);
		
		UnitTest();
		switch (cmd) {
		case CHEMSPELL:
			chemspell(target);
			break;
		case INCHI2SMILES:
			String smiles = inchi2smiles(target);
			System.out.println(smiles);
			break;
		case XML2IMPCHEM:
			String entryTag = optsource, idTag = targetIDs, chemTag = roType, xmlDB = actDB;
	        XMLToImportantChemicals xreader = new XMLToImportantChemicals(targetsFile, xmlDB, entryTag, idTag, chemTag);
	        xreader.process();
			break;
		case SMARTS_SEARCH:
			MongoDB db = new MongoDB( actHost, actPort, actDB );
			db.smartsMatchAllChemicals(target);
			break;
		case CONSISTENT_INCHI:
			String inchi = consistentInChI(target, "CMD_LINE");
			System.out.println(inchi);
			break;
		case SIMILARITY:
			double sim = similarity(actHost, actPort, actDB, target, optsource, simType);
			System.out.println(sim);
			break;
		case RENDER:
			render(target, "rendered.png", comment);
			break;
		case COFACTOR_AAMs:
			actServer.computeAAMsForCofactorPairs(actHost, actPort, actDB, cofactor_pair_file);
			break;
		case CHECK_COFACTORS:
			actServer.checkCofactors(actHost, actPort, actDB, lowUUID, highUUID);
			break;
		case INFER_OPS: 
			actServer.diffReactions(actHost, actPort, actDB, lowUUID, highUUID, rxns_white_list_file, !noAddToDB);
			break;
		case DUMP_TOP_ROS:
			RenderTopROJobs r = new RenderTopROJobs(actHost, actPort, actDB, outDir);
			r.renderTop(roType, numOps);
			break;
		case DUMP_OPS:
			actServer.outputOperators(actHost, actPort, actDB, outDir);
			break;
		case APPLY_RO: 
			if (substratesFile != null && roFile != null) {
				applyRO(substratesFile, roFile, true);
			} else if (roRep == -1) {
				// roRep not provided, assume that we do not want to lookup from DB, but instead RO is provided as is string
				applyRO(optsource, roType);
			} else {
				applyRO(actHost, actPort, actDB, optsource, roRep, roType);
			}
			// deprecated: actServer.applyRO_OnOneSubstrate(actHost, actPort, actDB, optsource, roRep, roType);
			break;
		case AUGMENT_NW:
			actServer.augmentNetwork(actHost, actPort, actDB, numOps, augmentWithROs, augmentedNetworkName, rxns_white_list_file);
			break;
		case FIND_ABSTRACT_PATH:
			List<String> targetSMILES_abs = null, targetCommonNames_abs = null;
			if (targetsFile != null) {
				P<List<String>, List<String>> targets = readTargetsFile(targetsFile, targetIDs);
				targetSMILES_abs = targets.fst();
				targetCommonNames_abs = targets.snd();
			}
			actServer.findAbstractPathway(actHost, actPort, actDB, targetSMILES_abs, targetCommonNames_abs, numOps, rxns_white_list_file);
			break;
		case FIND_PATH: 
			List<String> targetSMILES = null, targetCommonNames = null;
			if (targetsFile != null) {
				P<List<String>, List<String>> targets = readTargetsFile(targetsFile, targetIDs);
				targetSMILES = targets.fst();
				targetCommonNames = targets.snd();
			}
			actServer.findPathway(actHost, actPort, actDB, 
					optsource, target, targetSMILES, targetCommonNames, numSimilar, 
					numOps, maxNewChems, numPaths, augmentWithROs, augmentedNetworkName,
					addNativeSources, findConcrete, useFnGrpAbstraction, null, false);
			break;
		case RUN_GAME_SERVER:
			actServer.runGameServer(actHost, actPort, actDB);
			break;
			
		default: throw new Exception("Unknown command.");
		}
		if (!quiet)
			System.out.format("Successfully completed [%s]\n", cmd);
	}

	private static void applyRO(String substratesFile, String roFile, boolean b) {
		HashMap<Integer, String[]> substrates = readNumberedTuplesFile(substratesFile);
		HashMap<Integer, String[]> ros = readNumberedTuplesFile(roFile);
		for (int sid : substrates.keySet()) {
			for (int roid: ros.keySet()) {
				String roStr = ros.get(roid)[1];
				String subst = substrates.get(sid)[1];
				System.err.format("At substrate(%d)\t ro(%d)\n", sid, roid);
				RO ro = new ERO(new RxnWithWildCards(roStr, null, null)); 
				toStdOut(ActAdminServiceImpl.applyRO_OnOneSubstrate_DOTNotation(subst, ro), sid, roid);
			}
		}
	}

	private static void toStdOut(List<List<String>> products, Integer sid, Integer roid) {
		if (products == null)
			return;
		for (List<String> plist : products) {
			for (String p : plist) {
				// if sid and roid are specified then preface with two columns containing them.
				if (sid != null && roid != null)
					System.out.format("%d\t%d\t", sid, roid); 
				System.out.println(p);
			}
		}
	}

	private static void applyRO(String substrate, String roSMILES) {
		// here roType will be arbitrary ro SMILES, but note that it NEEDS to be a dot notation expanded SMILES
		RO ro = new ERO(new RxnWithWildCards(roSMILES, null, null)); 
		toStdOut(ActAdminServiceImpl.applyRO_OnOneSubstrate_DOTNotation(substrate, ro), null, null); 
	}

	private static void applyRO(String actHost, int actPort, String actDB, String substrate, int roRep, String roType) {
		// here roType will be one of "CRO" or "ERO"
		toStdOut(ActAdminServiceImpl.applyRO_OnOneSubstrate_DOTNotation(actHost, actPort, actDB, substrate, roRep, roType), null, null);
	}

	private static P<List<String>, List<String>> readTargetsFile(String targetsFile, String targetIDs) {
		HashMap<Integer, String[]> reads = readNumberedTuplesFile(targetsFile);
		Set<Integer> selected = parseSelectedIDs(targetIDs);
		List<String> smiles = new ArrayList<String>();
		List<String> names = new ArrayList<String>();
		for (Integer id : reads.keySet()) {
			if (selected != null && !selected.contains(id))
				continue;
			String[] row = reads.get(id);
			// format is: <id>\t<name>\t<smiles>\t<comments>
			smiles.add(row[2]);
			names.add(row[1]);
		}
		return new P<List<String>, List<String>>(smiles, names);
	}

	private static HashMap<Integer, String[]> readNumberedTuplesFile(String targetsFile) {
		HashMap<Integer, String[]> file = new HashMap<Integer, String[]>();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					new DataInputStream(new FileInputStream(targetsFile))));
			String line;
			while ((line = br.readLine()) != null) {
				if (line.length() == 0 || line.charAt(0) == '#')
					continue;
				String[] fields = line.split("\t");
				for (int i = 1 ; i < fields.length; i++)
					fields[i] = stripQuotes(fields[i].trim());
				file.put(Integer.parseInt(fields[0]), fields);
			}
		} catch (IOException e) {
			System.err.println("Could not read file: " + targetsFile);
			System.exit(-1);
		}
		return file;
	}

	private static String stripQuotes(String s) {
		int len = s.length() - 1;
		if (len == -1)
			return null;
		if (s.charAt(0) == '"' && s.charAt(len) == '"')
			return s.substring(1, len);
		System.err.println("Not well formatted targets file. Need to quote everything but the first field.\n" + s);
		System.exit(-1); 
		return null;
	}

	private static Set<Integer> parseSelectedIDs(String targetIDs) {
		// allowed: 1,3,5,8-100,2
		Set<Integer> S = new HashSet<Integer>();
		String[] elems = targetIDs.split(",");
		for (String e : elems) {
			if (e.indexOf('-') == -1) {
				S.add(Integer.parseInt(e));
			} else {
				String[] lr = e.split("-");
				int l = Integer.parseInt(lr[0]), r = Integer.parseInt(lr[1]);
				if (lr.length != 2 || l > r) {
					System.err.println("Invalid range specified: " + e);
					System.exit(-1);
				}
				for (int i = l; i<=r; i++)
					S.add(i);
			}
		}
		return S;
	}

	private static String inchi2smiles(String target) {
		Indigo indigo = new Indigo();
		IndigoInchi inchi = new IndigoInchi(indigo);
		IndigoObject o = inchi.loadMolecule(target);
		return o.smiles();
	}

	private static void clearAllStereoNotUsed(IndigoObject mol) {
    // the following three remove all stereo chemical
    // descriptors in the molecules.
    // That is good when we are calculating abstractions
    // of the molecule, but not when we are integrating
    // into the DB. we should keep the inchi as specified
    // in the reaction and the chemical's entry.

    // from the API:
    // clearStereocenters resets the chiral configurations of a molecule's atoms
    // clearAlleneCenters resets the chiral configurations of a molecule's allene-like fragments
    // clearCisTrans resets the cis-trans configurations of a molecule's bonds

    mol.clearAlleneCenters();
    mol.clearCisTrans();
    mol.clearStereocenters();
  }

	public static String consistentInChI(String in, String debug_tag) {

    String target = in;

    if (in.startsWith("InChI=")) {
		  // load the molecule and then dump out the inchi
		  // a round trip ensures that we will have it consistent
		  // with the rest of the system's inchis
      target = removeProtonation(in);
    }

    String target_rt = null;

    // Do a round trip through Indigo to canonicalize edge
    // case inchis that we might get into the system
		Indigo indigo = new Indigo();
		IndigoInchi inchi = new IndigoInchi(indigo);

		try {
			IndigoObject o;

      if (target.startsWith("InChI="))
				o = inchi.loadMolecule(target);
			else
				o = indigo.loadMolecule(target);

      // * Current installer/integrator does not clear off
      // * stereo centers. That is left to downstream stages
      // * that will encode more the biochemical insights
      // * So DO NOT call clearAllStereo here.
      // clearAllStereoNotUsed(o);

			target_rt = inchi.getInchi(o);
		} catch (Exception e) {
			System.err.println("consistentInChI failed [" + debug_tag + "]: " + target);
			target_rt = target;
		}

    // We now forcefully remove the /p
    // Sometimes the +ve charge is legit, e.g., N+ 
    // (N in a hetero ring w/ 4 bonds)
    // E.g., InChI=1S/C10H12N4O4S/c15-1-4-6(16)7(17)10(18-4)14-3-13-9(19)5-8(14)12-2-11-5/h2-4,6-7,10,15-17H,1H2,(H,11,12,19)/p+1/t4-,6-,7-,10-/m1/s1
    // Even though legit, we want the DB to be completely
    // devoid of /p's so remove but report
    String outr = removeProtonation(target_rt);

    // send note to output for cases where forcable removal as required
    if (!outr.equals(target_rt))
		  System.err.println("consistentInChI valid charge forcibly removed [" + debug_tag + "]: Data was:" + in + " Indigo RT: " + target_rt + " Final: " + outr);

    return outr;
	}

  public static String removeProtonation(String inchi) {
    // do not remove the proton when the inchi is a single proton!
    if (inchi.equals("InChI=1S/p+1") || inchi.equals("InChI=1/p+1"))
      return inchi;

    return inchi.replaceAll("/p[\\-+]\\d+", "");
  }

	private static double similarity(String host, int port, String dbs, String id1, String id2, String typ) {
		MongoDB db = new MongoDB( host, port, dbs );
		Indigo indigo = new Indigo(); IndigoInchi inchi = new IndigoInchi(indigo);
		IndigoObject o1, o2;
		if (typ.startsWith("MOL")) {
			o1 = molForID(Long.parseLong(id1), db, inchi);
			o2 = molForID(Long.parseLong(id2), db, inchi);
		} else if (typ.startsWith("RXN")) {	
			o1 = rxnForID(Long.parseLong(id1), db, indigo, inchi);
			o2 = rxnForID(Long.parseLong(id2), db, indigo, inchi);
		} else {
			o1 = o2 = null;
			System.err.println("Unrecognized similarity: Needs to be <RXN|MOL>:<tanimoto|tversky <alpha> <beta>|euclid-sub>");
			System.exit(-1);
		}
		String metric = typ.split(":")[1];
		return indigo.similarity(o1, o2, metric);
	}
	
	public static IndigoObject molForID(long id, MongoDB db, IndigoInchi inchi) {
		String in = db.getChemicalFromChemicalUUID(id).getInChI();
		return inchi.loadMolecule(in);
	}
	
	public static IndigoObject rxnForID(long id, MongoDB db, Indigo indigo, IndigoInchi inchi) {
		IndigoObject r = indigo.createReaction();
		Reaction rr = db.getReactionFromUUID(id);
		for (Long s : rr.getSubstrates())
			r.addReactant(molForID(s, db, inchi));
		for (Long p : rr.getProducts())
			r.addProduct(molForID(p, db, inchi));
		return r;
	}

	public static void render(String target, String fname, String comment) {
		Indigo indigo = new Indigo();
		IndigoObject o;
		// System.out.println("Indigo version " + indigo.version());
		// System.out.println("Rendering: " + target);
		if (target.contains("*") || target.contains("R")) {
			if (target.contains(">>")) {
				if (target.startsWith("InChI="))
					target = getSMILESFromInchiRxn(target, indigo);
				SMILES.renderReaction(o = indigo.loadQueryReaction(target), fname, comment, indigo);
			} else {
				SMILES.renderReaction(o = indigo.loadQueryMolecule(target), fname, comment, indigo);
			}
		} else {
			if (target.contains(">>")) {
				if (target.startsWith("InChI="))
					target = getSMILESFromInchiRxn(target, indigo);
				SMILES.renderReaction(o = indigo.loadReaction(target), fname, comment, indigo);
			} else {
				if (!target.startsWith("InChI=")) 
					SMILES.renderReaction(o = indigo.loadMolecule(target), fname, comment, indigo);
				else {
					IndigoInchi inchi = new IndigoInchi(indigo);
					SMILES.renderReaction(o = inchi.loadMolecule(target), fname, comment, indigo);
				}
			}
		}
		// System.out.println("Rendered: " + o.smiles());
	}
	
	private static String getSMILESFromInchiRxn(String inchiRxn, Indigo indigo) {
		// this is our internal hack when we want to use InChI's for substrates/products
		// the reaction is represented as s_inchi0(#)s_inchi1(#)>>(#)p_inchi0
		IndigoInchi inchi = new IndigoInchi(indigo);
		List<String> smiles = new ArrayList<String>();
		int end;
		int divide = 0;
		while ((end = inchiRxn.indexOf("(#)")) != -1) {
			String token = inchiRxn.substring(0,end);
			if (token.equals(">>")) {
				divide = smiles.size(); // remember which index the >> is at
				smiles.add(">>");
			} else {
				System.err.println("Converting to smiles: " + token);
				smiles.add(inchi.loadMolecule(token).canonicalSmiles());
			}
			inchiRxn = inchiRxn.substring(end + 3);
		}
		// add the last token to the end....
		smiles.add(inchi.loadMolecule(inchiRxn).canonicalSmiles());
		String smile = "";
		for (int i = 0; i < smiles.size(); i++) {
			smile += smiles.get(i);
			if (i != divide - 1 && i != divide)
				smile += ".";
		}
		return smile;
	}

	private static void chemspell(String target) {
		Canonicalizer canon = new Canonicalizer(null);
		List<Chemical> spelled = canon.canonicalize(target);
		int i=0;
		for (Chemical c : spelled) {
			String name = c.getCanon();
			boolean isApproximate = (c.getUuid() == -1);
			if (isApproximate)
				System.out.format("Maybe Option[%d] = %s\n", i++, name);
			else
				System.out.format("Option[%d] = %s\n", i++, name);
		}
	}

	private static boolean readBoolean(String arg) {
		return cmdline.hasOption(arg);
	}

	private static String read(String arg, String dflt) {
		// has the argument been passed?
		if( cmdline.hasOption( arg ) )
		    return cmdline.getOptionValue( arg );
		else 
			return dflt;
	}

	@SuppressWarnings("static-access")
	private static Options createOptions() {
		// create Options object
		Options options = new Options();
		
		Option help = new Option( "help", "print this message" );
		Option version = new Option( "version", "print the version information and exit" );
		Option quiet = new Option( "quiet", "be extra quiet" );
		Option verbose = new Option( "verbose", "be extra verbose" );
		Option debugl = new Option( "debuglevel", "debugging verbosity level" );
		
		/*
		 Option command  = OptionBuilder.withArgName( "property=value" )
                .hasArgs(2)
                .withValueSeparator()
                .withDescription( "desc" )
                .create( "D" );
        */

		Option execcmd = OptionBuilder.withArgName( "cmd" )
                .hasArg()
                .withDescription(  "execute the given Act command. One of [ INFER_OPS | FIND_PATH ]" )
                .create( "exec" );
		Option inferops_start = OptionBuilder.withArgName( "uuid" )
                .hasArg()
                .withDescription(  "the starting uuid to read for inferring operators" )
                .create( "start" );
		Option inferops_end = OptionBuilder.withArgName( "uuid" )
                .hasArg()
                .withDescription(  "the starting uuid to read for inferring operators" )
                .create( "end" );

		Option actHost = OptionBuilder.withArgName( "name" )
                .hasArg()
                .withDescription(  "the hostname on which the act mongodb data resides" )
                .create( "host" );
		Option actPort = OptionBuilder.withArgName( "num" )
                .hasArg()
                .withDescription(  "the port on which the act mongodb data resides" )
                .create( "port" );
		Option actdb = OptionBuilder.withArgName( "db_name" )
                .hasArg()
                .withDescription(  "the mongoDB dbs to use" )
                .create( "dbs" );
		Option outdir = OptionBuilder.withArgName( "dir" )
                .hasArg()
                .withDescription(  "the directory in which to dump out operator hierarchy" )
                .create( "outputdir" );
		Option config = OptionBuilder.withArgName( "config" )
                .hasArg()
                .withDescription(  "the location of the configuration file" )
                .create( "config" );
		Option cofactor_pair_file = OptionBuilder.withArgName( "cofactor_pair_file" )
                .hasArg()
                .withDescription(  "the file from which to read the cofactor pairs" )
                .create( "cofactor_pair_file" );
		Option target = OptionBuilder.withArgName( "chemical_name" )
                .hasArg()
                .withDescription(  "the end target to be queried, as its common name" )
                .create( "target" );
		Option comment = OptionBuilder.withArgName( "message" )
                .hasArg()
                .withDescription(  "descriptive text message" )
                .create( "comment" );
		Option targetsFile = OptionBuilder.withArgName( "filename" )
                .hasArg()
                .withDescription(  "the end target file" )
                .create( "targetsFile" );
		Option substratesFile = OptionBuilder.withArgName( "filename" )
                .hasArg()
                .withDescription(  "file with tuples id<tab>substrate smiles or inchis" )
                .create( "substratesFile" );
		Option rosFile = OptionBuilder.withArgName( "filename" )
                .hasArg()
                .withDescription(  "file with tuples id<tab>roSMARTS" )
                .create( "rosFile" );
		Option targetIDs = OptionBuilder.withArgName( "numlist" )
                .hasArg()
                .withDescription(  "the end targets, as a list, comma separated or ranges" )
                .create( "targetIDs" );
		Option numsimilar = OptionBuilder.withArgName( "num" )
                .hasArg()
                .withDescription(  "the number of similar compounds to lookup to generate paths to, for unnaturals and plain DFS search" )
                .create( "numsimilar" );
		Option rosteps_dfs = OptionBuilder.withArgName( "num" )
                .hasArg()
                .withDescription(  "-1 indicates plain DFS, anything else searches over and augmented graph with these number of RO instantiations (recursive) on each node" )
                .create( "rosteps_dfs" );
		Option augmentednw = OptionBuilder.withArgName( "network_collection_name" )
                .hasArg()
                .withDescription(  "-1 indicates plain DFS, anything else searches over and augmented graph with these number of RO instantiations (recursive) on each node" )
                .create( "augmentednw" );
		Option source = OptionBuilder.withArgName( "chemical_name" )
                .hasArg()
                .withDescription(  "the source of the pathway search. If use_natives enabled then this is just an additional chemical used to start from." )
                .create( "source" );
		Option maxchems = OptionBuilder.withArgName( "num" )
                .hasArg()
                .withDescription(  "the number of new chemicals we are allowed to generate during enumeration" )
                .create( "maxchems" );
		Option numops = OptionBuilder.withArgName( "num" )
                .hasArg()
                .withDescription(  "the numbers of operators (top-k) to pull out of Act operator DB" )
                .create( "numops" );
		Option rorep = OptionBuilder.withArgName( "rxn id" )
                .hasArg()
                .withDescription(  "the representative reaction, for which to pull up the corresponding RO" )
                .create( "ro_representative" );
		Option rotype = OptionBuilder.withArgName( "type" )
                .hasArg()
                .withDescription(  "the type of operators to dump out: {CRO, ERO, ARO}" )
                .create( "rotype" );
		Option simtype = OptionBuilder.withArgName( "type" )
                .hasArg()
                .withDescription(  "what is being compared: {RXN | MOL}:{tanimoto | tversky <alpha> <beta> | euclid-sub}" )
                .create( "simtype" );
		Option inferOpsForOnlyKnownGood = OptionBuilder.withArgName( "rxns_id_file" )
                .hasArg()
                .withDescription(  "restrict processing to known good reactions in datafile" )
                .create( "rxns_whitelist" );
		Option numpaths = OptionBuilder.withArgName( "num" )
                .hasArg()
                .withDescription(  "the numbers of paths to look for" )
                .create( "numpaths" );
		Option noAddToDB = new Option( "noAdd2db", "after operator inference, should we add them back to DB?" );
		Option findPathwayWithoutROs = new Option( "noROs", "should we find a non-speculated pathway?");
		Option useFnGrpAbstractionForSearch = new Option( "useFnGrpAbs", "should we function group abstraction (and refinement) for speculating pathways?");
		Option useNatives = new Option( "use_natives", "should we add the native metabolites as the starting point of the search?");
		
		options.addOption( help );
		options.addOption( version );
		options.addOption( quiet );
		options.addOption( verbose );
		options.addOption( debugl );
		options.addOption(execcmd);
		options.addOption(inferops_start);
		options.addOption(inferops_end);

		options.addOption(actHost);
		options.addOption(actPort);
		options.addOption(actdb);
		options.addOption(noAddToDB);
		options.addOption(findPathwayWithoutROs);
		options.addOption(useFnGrpAbstractionForSearch);
		options.addOption(outdir);
		options.addOption(config);
		options.addOption(target);
		options.addOption(comment);
		options.addOption(targetsFile);
		options.addOption(substratesFile);
		options.addOption(rosFile);
		options.addOption(targetIDs);
		options.addOption(source);
		options.addOption(cofactor_pair_file);
		options.addOption(maxchems);
		options.addOption(numops);
		options.addOption(numpaths);
		options.addOption(numsimilar);
		options.addOption(rosteps_dfs);
		options.addOption(rotype);
		options.addOption(simtype);
		options.addOption(rorep);
		options.addOption(inferOpsForOnlyKnownGood);
		options.addOption(useNatives);
		options.addOption(augmentednw);
		
		return options;
	}

	private static void printHelp(String prgname, Options options) {
		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( prgname, options );
	}

	private static void parseAndSetCmdLine(String[] args, Options options) throws ParseException {
		// create the parser
	    CommandLineParser parser = new GnuParser();
	    try {
	        // parse the command line arguments
	        cmdline = parser.parse( options, args );
	    }
	    catch( ParseException exp ) {
	        // oops, something went wrong
	        System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
	    }
		
	}

	@SuppressWarnings("unused")
	public static void UnitTest() {
		
		if (true)
			return;

		RxnTx.testEnumeration();
		
		Indigo ind = new Indigo();
		IndigoInchi ic = new IndigoInchi(ind);
		
		if (Configuration.getInstance().rxnSimplifyUsing == ReactionSimplification.HardCodedCoFactors) { // hardcodedfactors...
			// String invalid_mapping = "[CH:46]1[CH:44]=[N+:48]([CH:7]2[O:11][CH:10]([CH2:12][O:13][P:14]([O:17][P:18]([O:21][CH2:22][CH:23]3[O:27][CH:26]([N:28]4[C:32]5[N:33]=[CH:34][N:35]=[C:36]([NH2:37])[C:31]=5[N:30]=[CH:29]4)[CH:25]([OH:38])[CH:24]3[OH:39])([OH:20])=[O:19])([O-:51])=[O:15])[CH:9]([OH:40])[CH:8]2[OH:41])[CH:45]=[C:43]([C:42]([NH2:49])=[O:50])[CH:47]=1.C=CCO>>[CH:46]1[CH2:47][C:43]([C:42]([NH2:49])=[O:50])=[CH:45][N:48]([CH:7]2[O:11][CH:10]([CH2:12][O:13][P:14]([O:17][P:18]([O:21][CH2:22][CH:23]3[O:27][CH:26]([N:28]4[C:32]5[N:33]=[CH:34][N:35]=[C:36]([NH2:37])[C:31]=5[N:30]=[CH:29]4)[CH:25]([OH:38])[CH:24]3[OH:39])([OH:20])=[O:19])([OH:51])=[O:15])[CH:9]([OH:40])[CH:8]2[OH:41])[CH:44]=1.C=CC=O";
			String invalid_mapping = "[CH:46]1[CH:44]=[N+]([CH:7]2[O:11][CH:10]([CH2:12][O:13][P:14]([O:17][P:18]([O:21][CH2:22][CH:23]3[O:27][CH:26]([N:28]4[C:32]5[N:33]=[CH:34][N:35]=[C:36]([NH2:37])[C:31]=5[N:30]=[CH:29]4)[CH:25]([OH:38])[CH:24]3[OH:39])([OH:20])=[O:19])([O-])=[O:15])[CH:9]([OH:40])[CH:8]2[OH:41])[CH:45]=[C:43]([C:42]([NH2:49])=[O:50])[CH:47]=1.C=CCO>>[CH:46]1[CH2:47][C:43]([C:42]([NH2:49])=[O:50])=[CH:45][N]([CH:7]2[O:11][CH:10]([CH2:12][O:13][P:14]([O:17][P:18]([O:21][CH2:22][CH:23]3[O:27][CH:26]([N:28]4[C:32]5[N:33]=[CH:34][N:35]=[C:36]([NH2:37])[C:31]=5[N:30]=[CH:29]4)[CH:25]([OH:38])[CH:24]3[OH:39])([OH:20])=[O:19])([OH])=[O:15])[CH:9]([OH:40])[CH:8]2[OH:41])[CH:44]=1.C=CC=O";
			IndigoObject rr = ind.loadReaction(invalid_mapping);
			rr.automap("keep");
			System.out.println("SMILES: " + rr.smiles());
			SMILES.renderReaction(rr, "reactionTest.png", "comment", ind);
			// System.exit(-1);
		}
		
		// IndigoObject mol = ic.loadMolecule("InChI=1S/C3H9NO/c1-3(5)2-4/h3,5H,2,4H2,1H3");
		// System.out.format("Smiles: %s\n", mol.canonicalSmiles());
		
		// IndigoObject rr = ind.loadReaction("CCCO>>CCC=O");
		// IndigoObject rr = ind.loadReaction("[CH3:1][CH2:2][CH2:3][OH:4]>>[CH3:1][CH2:2][CH:3]=[O:4]");
		// rr.automap("keep");
		// System.out.println("SMILES: " + rr.smiles());
		// SMILES.renderReaction(rr, "reactionTest.png", "comment", ind);
		
		// String fixed = "O=C([*:1])[*:2]>>[*:1]C([*:2])([H])O[H]";
		// String fixed = "C(=O)[H,*:1]>>C"; // [*:1]C([*:2])([H])O[H]";
		// IndigoObject o = ind.loadQueryReaction(fixed);
		// ind.transform(o, mol = ind.loadMolecule("C(=O)C"));
		// System.out.format("Loaded: %s\n", mol.smiles());
		// System.out.format("Rxn smiles: %s\n", o.smiles());
		
		// Transform : { CC(C(C(=O)O)N)O>>CC(O)C(N)C(O)=O } by applying { [*]C([*])=O>>[*]C([*])([H])O[H] |$[*:0];;[*:6];;[*:0];;[*:6];;;$| } 
		// String rxnSmiles = "[*]C([*])=O>>[*]C([*])([H])O[H] |$[*:1];;[*:6];;[*:1];;[*:6];;;$|"; // doesn't work...
		String rxnSmiles = "[*:1]C([*:6])=O>>[*:1]C([*:6])([H])O[H]"; // works ...
		String c1s = "CC(C(C(=O)O)N)O";
		
		// String rxnSmiles = "[H]C([H,*:2])([H,*:3])C([H,*:5])([H,*:6])[H,*:7]>>C([*:2])([H,*:3])=C([H,*:5])([H,*:6])";
		// String c1s = "C1(Cl)C(Cl)C(Cl)C(Cl)C(Cl)C1(Cl)";
		
		// Enumerator e = new Enumerator(null);
		// RxnWithWildCards roWC = new RxnWithWildCards(rxnSmiles, null, null);
		// Chemical c1;
		// List<Chemical> c2;
		// RO ro = new RO(roWC);
		// c1 = new Chemical(-1, -1, c1s, c1s);
		// c2 = e.expandChemicalUsingOperator(c1, ro);
		// System.out.println(c2);
		
		// debug:
		// NC1C=CN(C2OC(COP(O)(=O)OP(O)(O)=O)C(O)C2O)C(O)N=1>>NC1C=CN(C2OC(COP(O)(=O)OP(O)(O)=O)C(O)C2=O)C(=O)N=1
		// operator: [H,*:1]C([H,*:2])([H])O[H]>>[H,*:1]C([H,*:2])=O
		
	}
}
