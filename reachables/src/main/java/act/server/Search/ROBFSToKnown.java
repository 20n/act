package act.server.Search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import act.server.ActAdminServiceImpl;
import act.server.EnumPath.OperatorSet;
import act.server.Molecules.CRO;
import act.server.SQLInterface.MongoDBPaths;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.ROApplication;
import act.shared.ReactionType;
import act.shared.SimplifiedReactionNetwork;
import act.shared.helpers.P;
import act.shared.helpers.T;

/**
 * Given an input unknown chemical, find a path to a known chemical.
 */

public class ROBFSToKnown {
	private static ActAdminServiceImpl actServer;
	private static int STEPS = 5;
	
	public static Path getPath(String inchi, SimplifiedReactionNetwork srn, Set<Long> ignore) {
		List<Path> paths = getPaths(inchi, srn, ignore, 1);
		if (paths.size() > 0) return paths.get(0);
		return null;
	}
	
	public static List<Path> getPaths(String inchi, SimplifiedReactionNetwork srn, Set<Long> ignore, int numConcretes) {
		Indigo indigo = new Indigo();
		//IndigoInchi indigoInchix = new IndigoInchi(indigox);
		//String target = indigoInchix.getInchi(indigox.loadMolecule("C=CC(C(=O)O)OCCCCCCCC"));
		
		actServer = new ActAdminServiceImpl(true /* dont start game server */);
		HashMap<Integer, OperatorSet> operators = actServer.getOperators(
				"localhost", 27017, "actv01", 10, "../../Installer/data/rxns-w-good-ros.txt"); // the rxns_list_file is "data/rxns-w-good-ros.txt"
		List<Path> paths = new ArrayList<Path>();

		/*
		for (CRO cro : ops.getAllCROs().values()) {
			System.out.println(cro);
			System.out.println("CRO " + cro.getQueryRxnString() + " " + cro.ID());
			
		}
		*/
		//Long start = System.currentTimeMillis();
		
		/**
		 * Chemical InChI mapping to the RO and parent chemical it was derived from.
		 */
		Map<String,P<ROApplication, String>> parents = new HashMap<String, P<ROApplication, String>>();
		parents.put(inchi, null);
		Set<Chemical> knownChemicals = new HashSet<Chemical>();
		
		Set<String> toExplore = new HashSet<String>();
		toExplore.add(inchi);
		for (int i = 0; i < STEPS; i++) {
			System.out.println("ROBFSToKnown.getPaths.STEP: " +  i);
			Set<String> allNewInchis = new HashSet<String>();
			
			for (String curInchi : toExplore) {
				List<ROApplication> outInchis = actServer.applyROs(curInchi, operators);
				Set<String> found = parents.keySet();
				for (ROApplication ro_inchis : outInchis) {
					List<String> newInchis = ro_inchis.products;
					for (String newInchi : newInchis) {
						if (found.contains(newInchi)) continue;
						Chemical chemical = srn.getChemical(newInchi);
						if (chemical != null && chemical.isCofactor()) continue;
						if (chemical != null && ignore !=null && ignore.contains(chemical.getUuid())) 
							continue;
						
						
						allNewInchis.add(newInchi);
						parents.put(newInchi, new P<ROApplication,String>(ro_inchis, curInchi));
						if (chemical != null){// && newInchi.equals(target)) {
							System.out.println("FOUND!!!");
							knownChemicals.add(chemical);
							if (knownChemicals.size() == numConcretes) break;
						}
					}
					if (knownChemicals.size() == numConcretes) break;
				}
				if (knownChemicals.size() == numConcretes) break;
			}
			if (knownChemicals.size() == numConcretes) break;
			toExplore = allNewInchis;
			//System.out.println(toExplore.size());
		}
		/*System.out.println("ROBFSToKnown.getPaths.parents.size: " + parents.size());
		System.out.println("ROBFSToKnown.getPaths.knownChemicals.size: " + knownChemicals.size());
		System.out.print("Time: ");
		System.out.println(System.currentTimeMillis() - start);
		*/
		if (knownChemicals.size() == 0) return paths;
		
		
		//backtrace
		for (Chemical knownChemical : knownChemicals) {
			System.out.println("Known Chemical " + knownChemical.getSmiles());
			List<Chemical> compounds = new ArrayList<Chemical>();
			List<T<Long, ReactionType, Double>> edgeList = new ArrayList<T<Long, ReactionType, Double>>();
			String curInchi = knownChemical.getInChI();
			compounds.add(knownChemical);
			Long nullID = Long.MAX_VALUE;
			IndigoInchi indigoInchi = new IndigoInchi(indigo);
			while (true) {
				//System.out.println(curInchi);
				P<ROApplication, String> parent = parents.get(curInchi);
				if (parent == null) break;
				curInchi = parent.snd();
				Chemical p = new Chemical(nullID);
				p.setInchi(curInchi);
				//System.out.println(parent.fst().roid);
				try {
					p.setSmiles(indigoInchi.loadMolecule(curInchi).smiles());
					System.out.println(p.getSmiles());
				} catch(Exception e) {
					System.err.println("Can't get SMILES");
					e.printStackTrace();
				}
				compounds.add(p);
				ROApplication roApp = parent.fst();
				edgeList.add(new T<Long, ReactionType, Double>((long)roApp.roid, roApp.type, roApp.probability));
			}

			Path path = new Path(compounds);
			path.setEdgeList(edgeList);
			paths.add(path);
		}
		
		
		return paths;
	}
	
	public static void main(String[] args) {
		MongoDBPaths db = new MongoDBPaths("pathway.berkeley.edu", 27017, "actv01"); // should get this from command line
		SimplifiedReactionNetwork srn = new SimplifiedReactionNetwork(db, "srn", null, true);
		String smiles = "C1CCN=C(O)CC1";
		Indigo indigo = new Indigo();
		IndigoInchi indigoInchi = new IndigoInchi(indigo);
		IndigoObject molecule = indigo.loadMolecule(smiles);
		String inchi = indigoInchi.getInchi(molecule);
		
		
		getPaths(inchi,srn,null,1);
		
	}
}
