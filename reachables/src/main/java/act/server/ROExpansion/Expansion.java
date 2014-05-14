package act.server.ROExpansion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.google.gwt.dev.util.Pair;
import com.mongodb.BasicDBList;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import act.server.AbstractSearch.AbstractReactionsHypergraph.IdType;
import act.server.AbstractSearch.AbstractSearch;
import act.server.AbstractSearch.CarbonSkeleton;
import act.server.AbstractSearch.MoleculeEquivalenceClass;
import act.server.AbstractSearch.ReactionEquivalenceClass;
import act.server.EnumPath.Enumerator;
import act.server.EnumPath.OperatorSet;
import act.server.EnumPath.OperatorSet.OpID;
import act.server.Molecules.BadRxns;
import act.server.Molecules.CRO;
import act.server.Molecules.DotNotation;
import act.server.Molecules.ERO;
import act.server.Molecules.RO;
import act.server.Molecules.SMILES;
import act.server.Molecules.TheoryROClasses;
import act.server.Molecules.TheoryROs;
import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDBPaths;
import act.server.Search.PathBFS;
import act.shared.AAMFailException;
import act.shared.AugmentedReactionNetwork;
import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.MalFormedReactionException;
import act.shared.NoSMILES4InChiException;
import act.shared.OperatorInferFailException;
import act.shared.ROApplication;
import act.shared.Reaction;
import act.server.ActAdminServiceImpl;

public class Expansion {
	HashMap<Integer, OperatorSet> ops;
	MongoDBPaths targetMongoDB; //where we want to put the curried eros, and contians chemicals corresonding to the chem ids in our reachables file
	MongoDBPaths sourceMongoDB; //where we get the eros
	HashMap<String,HashMap<Integer,List<CurriedERO>>> index = new HashMap<String,HashMap<Integer,List<CurriedERO>>>();
	int totalPatternCount = 0;
	int multiSubstrateCount = 0;
	int chemicalPatternMatches = 0;
	List<Chemical> reachables;
	HashSet<String> reached;
	BufferedWriter bw;
	
	public Expansion(int numops, String host, int port, String db, String host2, int port2, String db2, boolean makeNewEROs){
		this.sourceMongoDB = new MongoDBPaths(host, port, db); //contains eros
		this.targetMongoDB = new MongoDBPaths(host2,port2,db2); //place curried eros in, and get eros from
		this.reachables = getAllConcretelyReachable();
		this.reached = makeReached(this.reachables);
		if(makeNewEROs){
			makeIndex(numops);
			makeNewEROs();
		}
		else{
			List<CurriedERO> curriedEROs = this.targetMongoDB.getCurriedEROs();
			makeIndex(curriedEROs);
		}
	}
	
	public HashSet<String> makeReached(List<Chemical> chems){
		HashSet<String> reached = new HashSet<String>();
		for (Chemical c : chems){
			String smiles = c.getSmiles();
			if (smiles == null){
				continue;
				/*
		    	Indigo ind = new Indigo();
		    	IndigoInchi ic = new IndigoInchi(ind);
		    	String inchi = c.getInChI();
		    	try {
		    		smiles = ic.loadMolecule(inchi).canonicalSmiles();
		    	} catch(Exception e) {
		    		System.out.println("Failed to find SMILES for: " + inchi);
		    	}
		    	*/
			}
			reached.add(toDotNotation(smiles, new Indigo()));
		}
		return reached;
	}
	
	public static String toDotNotation(String substrateSMILES, Indigo indigo) {
		IndigoObject mol = indigo.loadMolecule(substrateSMILES);
		mol = DotNotation.ToDotNotationMol(mol);
		return mol.canonicalSmiles();
	}
	
	//makes the index from pre-existing curriedEROs
	public void makeIndex(List<CurriedERO> curriedEROs){
		for (CurriedERO ero : curriedEROs){
			addEROToIndex(ero);
			//System.out.println(ero.rxn());
		}
		System.out.println("Number of EROS in DB: "+curriedEROs.size());
	}

	//makes the index by reading EROs from the db
	public void makeIndex(int numops){
		//first get all our EROS
		List<ERO> eros = this.sourceMongoDB.eros(numops);
		for (ERO ero : eros){
			addEROToIndex(new CurriedERO(ero));
			addEROToIndex(new CurriedERO(ero.reverse()));
			//System.out.println((new CurriedERO(ero)).rxn());
			//System.out.println((new CurriedERO(ero.reverse())).rxn());
		}
		System.out.println("Number of EROs: "+eros.size()*2);
		System.out.println("Number of multi-substrate EROs: "+this.multiSubstrateCount);
		System.out.println("Number of patterns: "+this.totalPatternCount);
	}
	
	public void addEROToIndex (CurriedERO ero){
		String[] patterns = findPatterns(ero);
		int numSubstrates = patterns.length;
		if (numSubstrates > 1){
			this.multiSubstrateCount++;
		}
		for (String pattern : patterns){
			this.totalPatternCount++;
			
			HashMap<Integer,List<CurriedERO>> patternMap;
			if (this.index.containsKey(pattern)){ 
				patternMap = this.index.get(pattern);
			}
			else {
				patternMap = new HashMap<Integer,List<CurriedERO>>();
				this.index.put(pattern, patternMap);
			}
			
			List<CurriedERO> eroLs;
			if (patternMap.containsKey(numSubstrates)){
				eroLs = patternMap.get(numSubstrates);
			}
			else {
				eroLs = new ArrayList<CurriedERO>();
				patternMap.put(numSubstrates, eroLs);
			}
			eroLs.add(ero);
		}
	}
	
	public String[] findPatterns(CurriedERO ero){
		String eroStr = ero.rxn();
		String[] splitEroStr = eroStr.split(">>");
		if (splitEroStr.length == 0){ return splitEroStr; }
		String left = eroStr.split(">>")[0];
		return left.split("\\.");
	}
	
	public int numSubstrates(CurriedERO ero){
		String eroStr = ero.rxn();
		String left = eroStr.split(">>")[0];
		return left.split("\\.").length;
	}
	
	public void makeNewEROs(){
		int numChemicals = this.reachables.size();
		int counter = 0;
		for (Chemical c : this.reachables){
			counter++;
			System.out.println("Chemical "+ counter + " out of "+ numChemicals + " : "+c.getInChI());
			makeNewEROsWithChemical(c);
			//System.out.println("Average pattern matches per chemical so far: "+(((float) this.chemicalPatternMatches)/counter));
		}
	}
	
	public void makeNewEROsWithChemical(Chemical c){
		List<String> applicablePatterns = getApplicablePatterns(c);
		//System.out.println("Chemical: "+c+" --- "+applicablePatterns.size());
		this.chemicalPatternMatches+=applicablePatterns.size();
		for (String pattern : applicablePatterns){
			HashMap<Integer, List<CurriedERO>> eros = this.index.get(pattern);
			for (int numSubstrates : eros.keySet()){
				if (numSubstrates == 1){
					continue;
				}
				List<CurriedERO> multiSubstrateEROs = eros.get(numSubstrates);
				for (CurriedERO ero : multiSubstrateEROs){
					updateEROWithMatchingChemical(ero,c,pattern);
				}
			}
		}
	}
	
	public void updateEROWithMatchingChemical(CurriedERO ero, Chemical c, String pattern){
		//how do we know if a curriedERO for this reaction already exists?
		//if we just tried to remove an old ERO and make a curriedERO, how would we get all the isntances of the ERO in our index
		ero.addChemicalForPattern(c, pattern);
	}
	
	public List<String> getApplicablePatterns(Chemical c){
		String dotNotationSmiles = toDotNotation(c.getSmiles(),new Indigo());
		return getApplicablePatterns(dotNotationSmiles);
	}
	
	public List<String> getApplicablePatterns(String dotNotationSmiles){
		List<String> applicablePatterns = new ArrayList<String>();
		for (String pattern : index.keySet()){
			if (patternMatchesChem(pattern,dotNotationSmiles)){
				applicablePatterns.add(pattern);
			}
		}
		return applicablePatterns;
	}
	
	public static Boolean patternMatchesChem(String pattern, String dotNotationSmiles){
		if (pattern.equals("")){
			return false; //matching the empty pattern should never be able to give us a new chemical
		}
		try{
			Indigo indigo = new Indigo();
			IndigoObject patternMol = indigo.loadSmarts(pattern);
			IndigoObject chemMol = indigo.loadMolecule(dotNotationSmiles);
			IndigoObject matcher = indigo.substructureMatcher(chemMol);
			int numMatches = matcher.countMatches(patternMol);
			if (numMatches > 0){
				return true;
			}
		}
		catch(Exception e){
			//do nothing
		}
		return false;
	}
	
	//loads all reachables from a text file that lists reachables
	private List<Chemical> getAllConcretelyReachable() {
		// TODO Auto-generated method stub
		// Paul needs to run a wavefront starting from this.metaboliteChems and accumulate all reachables.
		List<Chemical> concretelyReachable = PathBFS.getReachables(this.targetMongoDB);
		//we use the targetDB because the chemicals we're allowed to use (the reachables) should correspond to the database where we're recording new reachables 
		return concretelyReachable;
	}
	
	public void unitTestPatternMatch(){
		assert patternMatchesChem("[H,*:1]C([H,*:2])([H,*:3])N([H])[H]","[H]C([H])([H])N([H])[H]") == true;
		assert patternMatchesChem("[H,*:1]C([H,*:2])([H,*:3])N([H])[H]","CC([H])([H])N([H])[H]") == true;
		assert patternMatchesChem("[H,*:1]C([H,*:2])([H,*:3])N([H])[H]","C1CCCCC1([H])N([H])[H]") == true;
		assert patternMatchesChem("[H,*:1]C([H,*:2])([H,*:3])N([H])[H]","[H]C([H])([H])N([H])C") == false;
		assert patternMatchesChem("[H,*:1]C([H,*:2])([H,*:3])N([H])[H]","[H]N([H])[H]") == false;
	}
	
	public float averageROsPerPattern(){
		int patternCount = 0;
		for (HashMap<Integer, List<CurriedERO>> ROs : this.index.values()){
			for (List<CurriedERO> ls : ROs.values()){
				patternCount+=ls.size();
			}
		}
		return ((float) patternCount)/this.index.size();
	}
	
	public void putCurriedEROsInDB(){
		List<CurriedERO> curriedEROs = new ArrayList<CurriedERO>();
		for (HashMap<Integer,List<CurriedERO>> e : this.index.values()){
			for (List<CurriedERO> ls : e.values()){
				for (CurriedERO ero : ls){
					if (!curriedEROs.contains(ero)){
						curriedEROs.add(ero);
					}
				}
			}
		}
		System.out.println("Number of curried EROs to store: "+curriedEROs.size());
		this.targetMongoDB.putCurriedEROs(curriedEROs);
	}
	
	public void record(String s){
		try{
			this.bw.write(s);
			this.bw.write(System.getProperty("line.separator"));
			this.bw.flush();
		}
		catch(Exception e){
			//nothing
		}
	}
	
	public void record(String s, int id){
		try{
			this.bw.write("["+id+"] "+s);
			this.bw.write(System.getProperty("line.separator"));
			this.bw.flush();
		}
		catch(Exception e){
			//nothing
		}
	}
	
    private static class ExpandOneChemical implements Runnable {
    	String smilesToExpand;
    	String smilesTarget;
    	int id;
    	Expansion expansion;
    	
    	ExpandOneChemical(String expand, String target, int i, Expansion e){
    		this.smilesToExpand = expand;
    		this.smilesTarget = target;
    		this.id = i;
    		this.expansion = e;
    	}
    	
	    public void run() {
			String str = "Chemical "+this.id;
			System.out.println("["+this.id+"] "+str);
			this.expansion.record(str,this.id);
			long start_chem = System.currentTimeMillis();
			this.expansion.record("new smiles: "+this.smilesToExpand,this.id);
			if (!this.expansion.reached.contains(this.smilesToExpand)){
				this.expansion.record("The current smiles ("+this.smilesToExpand+") is not known to be a reachable, but we'll add it to the reached set and see what we get.", this.id);
				this.expansion.reached.add(this.smilesToExpand);
			}
			//the item is now definitely in this.reached
			List<String> applicablePatterns = this.expansion.getApplicablePatterns(this.smilesToExpand);
			int counter_patterns = 0;
			for (String applicablePattern : applicablePatterns){
				counter_patterns++;
				String str2 = "Pattern "+counter_patterns+" out of "+applicablePatterns.size()+"  ---  ";
				System.out.println("["+this.id+"] "+str2);
				this.expansion.record(str2, this.id);
				this.expansion.record("new applicablePattern: "+applicablePattern, this.id);
				HashMap<Integer,List<CurriedERO>> numSubstratesToEROs = this.expansion.index.get(applicablePattern);
				for (Entry<Integer,List<CurriedERO>> entry : numSubstratesToEROs.entrySet()){
					System.out.println("["+this.id+"] "+entry.getKey()+" : "+entry.getValue().size()+"     ");
				}
				long start = System.currentTimeMillis();
				for (List<CurriedERO> eroLs : numSubstratesToEROs.values()){
					this.expansion.record("new ero ls", this.id);
					for (CurriedERO ero : eroLs){
						this.expansion.record("new ero in applicable ero from ero ls", this.id);
						HashMap<List<String>,List<List<String>>> result = ero.applyForPattern(this.smilesTarget, applicablePattern, this.expansion.bw);
						//record("result: "+result);
						for (Entry<List<String>,List<List<String>>> oneSubstrateSetResultsPair : result.entrySet()){
							List<String> substrates = oneSubstrateSetResultsPair.getKey();
							List<List<String>> oneSubstrateSetResults = oneSubstrateSetResultsPair.getValue();
							for (List<String> oneChemAlignmentResults : oneSubstrateSetResults){
								List<String> canonicalProducts = new ArrayList<String>();
								for (String dotSmiles : oneChemAlignmentResults){
									String canonicalSmiles = toDotNotation(dotSmiles, new Indigo());
									canonicalProducts.add(canonicalSmiles);
									if (canonicalSmiles.equals(this.smilesTarget)){
										//we've found it!
										String s1 = "we've found it!";
										String s2 = "substrates: "+substrates;
										String s3 = "products: "+canonicalProducts;
										String s4 = "rxn: "+ero.rxn();
										String s5 = "rxn id: "+ero.ID();
										System.out.println("["+this.id+"] "+s1);
										System.out.println("["+this.id+"] "+s2);
										System.out.println("["+this.id+"] "+s3);
										System.out.println("["+this.id+"] "+s4);
										System.out.println("["+this.id+"] "+s5);
										this.expansion.record(s1,this.id);
										this.expansion.record(s2,this.id);
										this.expansion.record(s3,this.id);
										this.expansion.record(s4,this.id);
										this.expansion.record(s5,this.id);
									}
									if (!this.expansion.reached.contains(canonicalSmiles)){
										this.expansion.record("FOUND NEW SMILES: "+canonicalSmiles, this.id);
										this.expansion.reached.add(canonicalSmiles);
									}
								}
								//this.expansion.targetMongoDB.addEROActFamily(substrates,ero,canonicalProducts);
							}
						}
					}
				}
				long end = System.currentTimeMillis();
				String str3 = "-    Time: "+(end-start);
				System.out.println("["+this.id+"] "+str3);
				this.expansion.record(str3, this.id);
			}
			long end_chem = System.currentTimeMillis();
			String str4 = "Chemical Time: "+(end_chem-start_chem);
			System.out.println("["+this.id+"] "+str4);
			this.expansion.record(str4, this.id);
	    }
	}
	
	public void expandInSearchOf(List<String> dotNotationSmiles, String dotNotationSmilesTarget){
		int counter = 0;
		try{
			File file = new File("expansionLog.txt");
			file.createNewFile();
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			this.bw = new BufferedWriter(fw);
		}
		catch(Exception e){
			//nothing
		}
		for (String smiles : dotNotationSmiles){
			counter++;
			ExpandOneChemical c = new ExpandOneChemical(smiles,dotNotationSmilesTarget,counter,this);
	        Thread t = new Thread(c);
	        t.start();
		}
		System.out.println("Finished");
	}
	
	public void expand(List<String> dotNotationSmiles){
		int counter = 0;
		Indigo indigo = new Indigo();
		try{
			File file = new File("expansionLog.txt");
			file.createNewFile();
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			this.bw = new BufferedWriter(fw);
		}
		catch(Exception e){
			//nothing
		}
		for (String smiles : dotNotationSmiles){
			counter++;
			String str = "Chemical "+counter+" out of "+dotNotationSmiles.size();
			System.out.println(str);
			record(str);
			long start_chem = System.currentTimeMillis();
			record("new smiles: "+smiles);
			if (!this.reached.contains(smiles)){
				record("The current smiles ("+smiles+") is not known to be a reachable, but we'll add it to the reached set and see what we get.");
				this.reached.add(smiles);
			}
			//the item is now definitely in this.reached
			List<String> applicablePatterns = this.getApplicablePatterns(smiles);
			int counter_patterns = 0;
			for (String applicablePattern : applicablePatterns){
				counter_patterns++;
				String str2 = "Pattern "+counter_patterns+" out of "+applicablePatterns.size()+"  ---  ";
				System.out.print(str2);
				record(str2);
				record("new applicablePattern: "+applicablePattern);
				HashMap<Integer,List<CurriedERO>> numSubstratesToEROs = this.index.get(applicablePattern);
				for (Entry<Integer,List<CurriedERO>> entry : numSubstratesToEROs.entrySet()){
					System.out.print(entry.getKey()+" : "+entry.getValue().size()+"     ");
				}
				long start = System.currentTimeMillis();
				for (List<CurriedERO> eroLs : numSubstratesToEROs.values()){
					record("new ero ls");
					for (CurriedERO ero : eroLs){
						record("new ero in applicable ero from ero ls");
						HashMap<List<String>,List<List<String>>> result = ero.applyForPattern(smiles, applicablePattern, this.bw);
						//record("result: "+result);
						for (Entry<List<String>,List<List<String>>> oneSubstrateSetResultsPair : result.entrySet()){
							List<String> substrates = oneSubstrateSetResultsPair.getKey();
							List<List<String>> oneSubstrateSetResults = oneSubstrateSetResultsPair.getValue();
							//System.out.println("new substrate set");
							for (List<String> oneChemAlignmentResults : oneSubstrateSetResults){
								//System.out.println("new chem alignment");
								List<String> canonicalProducts = new ArrayList<String>();
								for (String dotSmiles : oneChemAlignmentResults){
									String canonicalSmiles = toDotNotation(dotSmiles, indigo);
									//System.out.println("canonicalSmiles: "+canonicalSmiles);
									canonicalProducts.add(canonicalSmiles);
									if (!this.reached.contains(canonicalSmiles)){
										//System.out.println("****NEW: "+canonicalSmiles);
										this.reached.add(canonicalSmiles);
									}
								}
								this.targetMongoDB.addEROActFamily(substrates,ero,canonicalProducts);
							}
						}
					}
				}
				long end = System.currentTimeMillis();
				String str3 = "-    Time: "+(end-start);
				System.out.println(str3);
				record(str3);
			}
		long end_chem = System.currentTimeMillis();
		String str4 = "Chemical Time: "+(end_chem-start_chem);
		System.out.println(str4);
		record(str4);
		}
	}
		
	public void findChemicalWithOneEROStep(String smiles){
		AbstractSearch as = new AbstractSearch(this.sourceMongoDB,null); //sourceMongoDB has the right chem ids
		CarbonSkeleton skeleton = new CarbonSkeleton(smiles);
		MoleculeEquivalenceClass equivClass = as.getSameSkeletonChemicalsFromSkeleton(skeleton);
		List<String> dotNotationCanonicalSmiles = new ArrayList<String>();
		for (long uuid : equivClass.matchingChemicalUUIDs){
			Chemical c = this.sourceMongoDB.getChemicalFromChemicalUUID(uuid);
			String s = c.getSmiles();
			System.out.println(s);
			String canonicalSmiles = this.toDotNotation(s, new Indigo());
			dotNotationCanonicalSmiles.add(canonicalSmiles);
		}
		String dotNotationSmilesTarget = this.toDotNotation(smiles, new Indigo());
		this.expandInSearchOf(dotNotationCanonicalSmiles, dotNotationSmilesTarget);
	}

	public static void main(String[] args){
		
		boolean makeNewEROs = false;
		int numops = 4000; //we don't actually have 4,000 so it will be less than this
		
		Expansion exp = new Expansion(numops,"localhost",27017,"actv01","localhost",27017,"actv01", makeNewEROs);
		
		//TODO: decide how to represent new EROs - associate each substrate index with the set of chems that can be used to fill it?
		//if the above, would have to maybe do something better than map from pattern to ero, might have to indicate which item it was supposed to fill
		//better to make a new 'ero' for each chem filling? or just associate the set of chems with the original ero and iterate through?
		//also have to make the version of applyROs that can use this info
		//TODO: decide if we want to combine making new EROs with making new chemicals (by using all the 1-substrate EROs)  probably not
		String task = "enumerate";
		
		if (makeNewEROs){
			long startTime = System.nanoTime();
			exp.makeIndex(numops);
			long middleTime = System.nanoTime();
			System.out.println("Number of unique patterns: "+exp.index.size());
			System.out.println("Average number of EROs that contain a given pattern: "+exp.averageROsPerPattern());
			exp.makeNewEROs();
			System.out.println("Average number of patterns matched by a reachable: "+((float) exp.chemicalPatternMatches)/exp.reachables.size());
			long endTime = System.nanoTime();
			System.out.println("Time to map reachables to multi-substrate EROs: "+ ((endTime-middleTime)/1000000000));
			exp.putCurriedEROsInDB();
		}
		else{
			if (task.equals("expand")){
				//let's use our curried EROs to expand to depth 1, feed in all the reachables and expand with those (not changing the set of EROs, just applying EROs)
				List<String> reachables = new ArrayList<String>();
				reachables.addAll(exp.reached);
				exp.expand(reachables);
			}
			else if (task.equals("findOne")){
				String nylon_7_smiles = "NCCCCCCC(=O)O";
				exp.findChemicalWithOneEROStep(nylon_7_smiles);
			}
            else if (task.equals("enumerate")) {
                exp.enumerateEROSubstrates();
            }
		}
		System.out.println("Finished.");
	}

    private void enumerateEROSubstrates() {
        List<CurriedERO> curriedEROs = this.targetMongoDB.getCurriedEROs();
        long total = 0;
        int largeNumberEro = 0;
        for(CurriedERO ero : curriedEROs) {
            int count = ero.countSubstrateLists();
            if(count > 100000) {
                String foo = ero.serialize();
                CurriedERO bar = CurriedERO.deserialize(foo);
                ero.countSubstrateLists();
                ero.ID();
                largeNumberEro++;

                String substratePatterns = "";
                for (int i = 0; i < 3; i++) {
                    if (i <= ero.patterns.length - 1) {
                        substratePatterns += "\"";
                        substratePatterns += ero.patterns[i];
                        substratePatterns += "\"";
                    }
                    substratePatterns += ",";
                }
                System.out.printf("\"%d\",%s\"%s\"\n", count, substratePatterns, ero.ro.toString());
            }
            total += count;
        }
        System.out.println(total);
        System.out.println(largeNumberEro);
    }
}