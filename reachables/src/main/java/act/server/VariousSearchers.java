package act.server;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import act.render.RenderPathways;

import act.server.EnumPath.PathToNativeMetabolites;
import act.server.FnGrpDomain.FnGrpDomainSearch;
import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDBPaths;
import act.server.Search.PathBFS;
import act.server.Search.ROBFSToKnown;
import act.server.Search.SimpleConcretePath;
import act.server.Search.SimplifiedUCS;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.SimplifiedReactionNetwork;

public class VariousSearchers {

	MongoDBPaths DB;
	Strategy strategy;
	Chemical chem; Long chemUUID; 
	String targetInchi;
	List<String> targetSMILES, targetNames;
	Chemical optionalStart;
	int maxDepthInDFS;
	String toplevelDir;
	int numSimilar;
	int numPaths;
	int numOps;
	int numHopsForROAugmentation;
	int maxArtificiallyGeneratedChems;
	List<Chemical> metaboliteChems;
	String augmentedNwName;
	
	Set<Long> ignoredChemicals;
	
	
	public enum Strategy { FNGRPS, ENUM, DFS, DFS_TO_SIMILAR, OLDBFS, RO_AUGMENTED_DFS, WEIGHTED}

	public void setNetworkName(String dbName) { this.augmentedNwName = dbName; }
	public void setStrategy(Strategy s) { this.strategy = s; }
	public void setMaxDepthInDFS(int depth) { this.maxDepthInDFS = depth; }
	public void setNumPaths(int num) { this.numPaths = num; }
	public void setNumOpsToLookupFromDB(int num) { this.numOps = num; }
	public void setNumMaxChemicalsInEnumeration(int num) { this.maxArtificiallyGeneratedChems = num; }
	public void setHopsForROAugmentation(int numHops) { this.numHopsForROAugmentation = numHops; }
	public void setTopLevelDirForOutput(String toplevelDir) { this.toplevelDir = toplevelDir; }
	public void setTarget(String name) { 
		this.chemUUID = (long) -1;
		this.chem = null;
		if (name.startsWith("SMILES:")) {	
			System.out.println("Name: " + name);
			String smiles = name.substring(7);
			Indigo indigo = new Indigo();
			IndigoInchi indigoInchi = new IndigoInchi(indigo);
			try {
				IndigoObject molecule = indigo.loadMolecule(smiles);
				Chemical chemical = this.DB.getChemicalFromSMILES(smiles);
				if (chemical == null) {
					this.targetInchi = indigoInchi.getInchi(molecule);
					chemical = this.DB.getChemicalFromInChI(this.targetInchi);	
				} 
				
				if (chemical != null) {
					this.chem = chemical;
					this.chemUUID = chemical.getUuid();
				}
			} catch (Exception e) {
				//failed to load with SMILES
			}
		} else if (name.startsWith("InChI=")) {
			try {
				Indigo indigo = new Indigo();
				IndigoInchi indigoInchi = new IndigoInchi(indigo);
				IndigoObject molecule = indigoInchi.loadMolecule(name);
				String inchi = indigoInchi.getInchi(molecule);
				this.chem = this.DB.getChemicalFromInChI(inchi);	
				if (this.chem != null) 
					this.chemUUID = this.chem.getUuid();
				else
					this.targetInchi = name;
			} catch (Exception e) {
			}
		} else {
			this.chemUUID = name == null ? -1 : this.DB.getChemicalIDFromName(name);
			this.chem = this.chemUUID == -1 ? null : this.DB.getChemicalFromChemicalUUID(this.chemUUID);
		}
	}
	public void setTargetSMILES(List<String> s, List<String> n) { this.targetSMILES = s; this.targetNames = n; }
	public void setNumSimilarToLookupFor(int num) { this.numSimilar = num; }
	public void setOptionalStart(String optionalSrc) {
		this.optionalStart = optionalSrc == null ? null: this.DB.getChemicalFromChemicalUUID(this.DB.getChemicalIDFromName(optionalSrc));
	}
	public void setIgnore(Set<Long> ignoredChemicals) { 
		this.ignoredChemicals = new HashSet<Long>();
		List<Chemical> cofactors = DB.getCofactorChemicals();
		for (Chemical chemical : cofactors) {
			this.ignoredChemicals.add(chemical.getUuid());
		}
		if (ignoredChemicals != null)
			this.ignoredChemicals.addAll(ignoredChemicals);
		
		System.out.println("setIgnore" + this.ignoredChemicals.size());
	}
	
	public VariousSearchers(MongoDBPaths mongoDB, boolean addNatives) {
		this.DB = mongoDB;
		this.strategy = Strategy.DFS; // default search is over the concrete reactions
		this.metaboliteChems = addNatives ? this.DB.getNativeMetaboliteChems() : new ArrayList<Chemical>();
	}

	public List<Path> searchPaths() {
		List<Path> results = null;
		switch (this.strategy) {
		case FNGRPS:
			results = fngrps();
			break;
		case ENUM:
			results = enumerate();
			break;
		case DFS:
			results = DSF();
			break;
		case DFS_TO_SIMILAR:
			results = DFS2similar();
			break;
		case RO_AUGMENTED_DFS:
			results = roAugmentedDFS();
			break;
		case WEIGHTED:
			results = weightedSearch();
			break;
		case OLDBFS:
			results = oldBFS();
			break;
		default:
			System.out.println("Unrecognized search strategy: " + this.strategy);
			System.exit(-1);
			return null;
		}
		/*
		for (Path path : results) {
			List<String> svgs = new ArrayList<String>();
			for (Chemical c : path.getCompoundList()) {
				String svg = RenderChemical.getSvg(c);
				svgs.add(svg);
			}
			path.setCompoundSvgs(svgs);
		}*/
		
		System.out.println("returning paths");
		if (results != null) {
			for(Path p : results) {
				System.out.println(p.pathID);
				List<Chemical> chemicals = p.getCompoundList();
				for (Chemical c : chemicals) {
					System.out.println(c.getUuid());
				}
			}
		}
		return results;
		/*
		if (false) {
			AbstractPath pathFinder = new AbstractPath(mongoDB, metabolites);
			if (true) throw new IllegalArgumentException("Non-enumerated path exploration not done yet. AbstractPath is not completely implemented...");
			return pathFinder.findPath(chem);
		}
		*/
	}
	
	private List<Path> fngrps() {
		System.out.println("Speculating paths to target: " + chem);
		System.out.println("(Null ok) Starting chemical: " + optionalStart);
		if (optionalStart != null) metaboliteChems.add(optionalStart);
		
		// no not enumerate, but instead use more efficient functional group abstraction...
		FnGrpDomainSearch abstractPathFinder = new FnGrpDomainSearch(chem, metaboliteChems, numOps, this.DB);
		return abstractPathFinder.getPaths(numPaths);
	}
	
	private List<Path> enumerate() {
		System.out.println("Speculating paths to target: " + chem);
		System.out.println("(Null ok) Starting chemical: " + optionalStart);
		if (optionalStart != null) metaboliteChems.add(optionalStart);
		
		// enumerate...
		PathToNativeMetabolites pathFinder = new PathToNativeMetabolites(chem, metaboliteChems, numOps, maxArtificiallyGeneratedChems, this.DB);
		return pathFinder.getPaths(numPaths);
	}
	
	private static SimplifiedReactionNetwork srn;
	private List<Path> DSF() {
		List<Path> finalPaths = new ArrayList<Path>();
		
		HashSet<Long> startingChems = new HashSet<Long>();
		if (this.optionalStart != null) 
			startingChems.add(this.optionalStart.getUuid());
		else
			startingChems = chemicalsToIDs(this.metaboliteChems);
		
		if (srn == null) {
			srn = new SimplifiedReactionNetwork(DB, "srn", DB.getCofactorChemicals() ,true);
		}
		//SimplifiedReactionNetwork srn = new SimplifiedReactionNetwork(DB, "onestepro", DB.getCofactorChemicals() ,true);
		// SimplifiedReactionNetwork srn = new SimplifiedReactionNetwork(DB, "onestepro", DB.getCofactorChemicals() ,true);
		Path roPath = getToKnownPath();
		if (roPath != null) {
			this.chem = roPath.getCompoundList().get(0);
			this.chemUUID = this.chem.getUuid();
		} else if (this.chem == null) {
			return finalPaths;
		}
		
		SimpleConcretePath scp = new SimpleConcretePath(srn);
		scp.setIgnored(ignoredChemicals);
		scp.findSimplePaths(this.chemUUID, startingChems, numPaths, maxDepthInDFS);
		List<Path> paths = scp.getPaths();
		for(Path p : paths) {
			if (roPath != null) {
				p.getCompoundList().remove(0);
				p.reverse();
				finalPaths.add(Path.concat(p, roPath));
			} else {
				p.reverse();
				finalPaths.add(p);
			}
		}
		/*
		for(Path p : paths)
			RenderPathways.renderCompoundImages(p, this.DB, "concretePaths", toplevelDir);	
		*/
		
		return finalPaths;
	}
	private Path getToKnownPath() {
		Path roPath = null;
		if (this.chem == null && targetInchi != null) {
			roPath = ROBFSToKnown.getPath(targetInchi, srn, ignoredChemicals);
		}
		return roPath;
	}
	
	private List<Path> roAugmentedDFS() {
		HashSet<Long> startingChems = new HashSet<Long>();
		if (this.optionalStart != null) 
			startingChems.add(this.optionalStart.getUuid());
		else
			startingChems = chemicalsToIDs(this.metaboliteChems);
		
		System.out.format("Finding paths to targets: %s\n%s\n", this.targetNames, this.targetSMILES );
		
		// Use the augmented network to do the DFS search
		SimplifiedReactionNetwork srn = new SimplifiedReactionNetwork(DB, this.augmentedNwName, DB.getCofactorChemicals() ,true);
		HashMap<String, Long> targetUUIDs = addTargetsToNetwork(srn);
		SimpleConcretePath scp = new SimpleConcretePath(srn);
		scp.setIgnored(ignoredChemicals);
		
		List<Path> paths = new ArrayList<Path>();
		for (String target : targetUUIDs.keySet()) {
			System.out.format("---- Finding paths to target %s/id=%s\n", target, targetUUIDs.get(target));
			scp.findSimplePaths(targetUUIDs.get(target), startingChems, numPaths, maxDepthInDFS);
			List<Path> pathsToTarget = scp.getPaths();
			for(Path p : pathsToTarget)
				RenderPathways.renderCompoundImages(p, this.DB, "roAugmentedPaths", toplevelDir);
			paths.addAll(pathsToTarget);
		}
		
		return paths;
	}
	
	private List<Path> weightedSearch() {
		List<Path> finalPaths = new ArrayList<Path>();
		Path roPath = getToKnownPath();
		if (roPath != null) {
			this.chem = roPath.getCompoundList().get(0);
			this.chemUUID = this.chem.getUuid();
		} else if (this.chem == null) {
			return finalPaths;
		}
		
		if (srn == null) {
			srn = new SimplifiedReactionNetwork(DB, "srn", DB.getCofactorChemicals() ,true);
		}
		SimplifiedUCS ucs = new SimplifiedUCS(srn, chemicalsToIDs(this.metaboliteChems));
		ucs.setIgnore(ignoredChemicals);
		Path path = ucs.getPath(this.chem);
		if (path == null) return null;
		
		if (roPath != null) {
			path.getCompoundList().remove(path.getCompoundList().size() - 1);
			finalPaths.add(Path.concat(path, roPath));
		} else {
			finalPaths.add(path);
		}

		return finalPaths;
	}
	
	private HashMap<String, Long> addTargetsToNetwork(SimplifiedReactionNetwork srn) {
		// TODO Auto-generated method stub
		/* First add the new target to the network */ 
		// this also adds the target to this.nodes, and this.notExpanded. (through createNode)
		HashMap<String, Long> targetUUIDs;
		targetUUIDs = new HashMap<String, Long>();
		for (int i = 0; i<targetSMILES.size(); i++) {
			String target = targetSMILES.get(i);
			String name = targetNames.get(i);
			targetUUIDs.put(target, addTargetIfNotPresent(srn, target, name));
		}
		return targetUUIDs;
	}
	
	private Long addTargetIfNotPresent(SimplifiedReactionNetwork srn, String targetSMILES, String targetName) {
		Indigo indigo = new Indigo();
		IndigoInchi inchi = new IndigoInchi(indigo);
		String targetInchi;
		if (!targetSMILES.startsWith("InChI=")) {
			IndigoObject target = indigo.loadMolecule(targetSMILES);
			targetInchi = inchi.getInchi(target);
		} else
			targetInchi = targetSMILES;
		
		System.out.println("Target inchi: " + targetInchi);
		Long targetID = createNode(srn, targetInchi);
		
		// potentially add a self loop....
		Chemical dummy = new Chemical(targetID);
		dummy.setCanon(targetName);
		dummy.setInchi(targetInchi);
		srn.addChemicalObject(dummy);
		
		System.err.println("---- Added target: " + dummy);
		
		return targetID;
	}
	
	private Long createNode(SimplifiedReactionNetwork srn, String inchi) {
		// need to double check if the node already exists, if so just return the old ID
		// if the node does not exist then cook up an ID -- to make sure it is distinct we use NEGATIVES
		Chemical chem = srn.getChemical(inchi);
		return chem == null ? 
				srn.getMinChemicalId() - 1 // create new min chem id 
				: chem.getUuid(); // return old id
	}
	
	public void roAugmentTheNetwork() {
	}

	private List<Path> DFS2similar() {
		HashSet<Long> startingChems = new HashSet<Long>();
		if (optionalStart != null) 
			startingChems.add(optionalStart.getUuid());
		else
			startingChems = chemicalsToIDs(this.metaboliteChems);
		// we are not getting anything out of target, even if contains something...
		Indigo indigo = new Indigo();

		List<Path> paths = new ArrayList<Path>();
		for (String target : this.targetSMILES) {
			List<Long> endIDs = this.DB.getMostSimilarChemicalsToNewChemical(target, numSimilar, indigo, new IndigoInchi(indigo));
			System.out.format("Input Chem: %s\nSimilar chem: %s\n", target, endIDs.toString());
			for (Long endID : endIDs) {
				SimpleConcretePath scp = new SimpleConcretePath();
				scp.findSimplePaths(endID, startingChems, numPaths, maxDepthInDFS);
				paths.addAll(scp.getPaths());
			}
			String dir = "similarPaths-" + escapeSlashes(target) + "/";
			new File(dir).mkdir();
			for(Path p : paths)
				RenderPathways.renderCompoundImages(p, this.DB, dir, toplevelDir);
			// the "/" above makes the similar paths as a common directory under which different similar chems dir are created.
			SMILES.renderMolecule(indigo.loadMolecule(target), dir + "target.png", "", indigo);
		}
		return paths;
	}
	
	private String escapeSlashes(String s) {
		return s.replaceAll("/", "\\/");
	}
	
	private List<Path> oldBFS() {
		PathBFS pathFinder = new PathBFS(this.DB, chemicalsToIDs(this.metaboliteChems));
		System.out.println("Finding paths to target: " + chem);
		pathFinder.getPaths(chem);
		return null;
	}

	public static HashSet<Long> chemicalsToIDs(List<Chemical> chems) {
		HashSet<Long> chemIDs = new HashSet<Long>();
		for (Chemical c : chems)
			chemIDs.add(c.getUuid());
		return chemIDs;
	}

}


