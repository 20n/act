package act.installer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import act.installer.kegg.KeggParser;
import act.installer.metacyc.MetaCyc;
import act.installer.sequence.SwissProt;
import act.installer.SeqIdentMapper;
import act.shared.sar.SARInfer;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import org.json.JSONObject;

import act.client.CommandLineRun;
import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.helpers.P;
import act.installer.ectoact.*;
import act.installer.patents.FTO;


public class Main {
	private String brenda, chemicals, taxonomy, names, brendaNames, cofactors, cofactor_pair_AAM, natives, litmining_chem_cleanup, imp_chems; //file names
	private MongoDB db;
	private FileWriter chem, org;
	private HashSet<String> missingChems, missingOrgs;
	
	public Main(String brenda, String taxonomy, String names, String chemicals, String brendaNames, String cofactors, String cofactor_pair_AAM, String natives, String litmining_chem_cleanup, String imp_chems, String path, String host, int port, String dbs) {
		this.brenda = path + "/" + brenda;
		this.taxonomy = path + "/" + taxonomy;
		this.names = path + "/" + names;
		this.chemicals = path + "/" + chemicals;
		this.brendaNames = path + "/" + brendaNames;
		this.cofactors = path + "/" + cofactors;
		this.cofactor_pair_AAM = path + "/" + cofactor_pair_AAM;
		this.natives = path + "/" + natives;
		this.litmining_chem_cleanup = path + "/" + litmining_chem_cleanup;
		this.imp_chems = path + "/" + imp_chems;
		db = new MongoDB(host, port, dbs);
		missingChems = new HashSet<String>();
		missingOrgs = new HashSet<String>();
	}
	
	public void addOrganisms() {
		Long nil = new Long(-1); //dont know what to put for ncbi
		try{
			FileInputStream fstream = new FileInputStream(taxonomy);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   {
				//String fieldsTogether = strLine.replaceAll("\\s","");
				String[] fields = strLine.split("\\|");
				Organism o = new Organism(Long.parseLong(fields[0].trim()), nil, null);
				o.setParent(Long.parseLong(fields[1].trim()));
				o.setRank(fields[2].trim());
				db.submitToActOrganismDB(o);
			}
			//Close the input stream
			in.close();
			
			FileInputStream nameStream = new FileInputStream(names);
			DataInputStream nameIn = new DataInputStream(nameStream);
			br = new BufferedReader(new InputStreamReader(nameIn));
			
			while((strLine = br.readLine()) != null) {
				String[] fields = strLine.split("\\|");
				Organism o = new Organism(Long.parseLong(fields[0].trim()), nil, fields[1].trim());
				db.submitToActOrganismNameDB(o);
			}
		}catch (Exception e){//Catch exception if any
			e.printStackTrace();
		}
	}
	
	public void addBrendaNames() {
		try {
			
			
			FileInputStream fstream = new FileInputStream(brendaNames);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			int cnt = 0;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   {
				String[] fields = strLine.split("\\t");
				if(fields.length < 2) {
					System.err.println(strLine);
				} else {
					String inchi = fields[1].trim();
					inchi = CommandLineRun.consistentInChI(inchi, "Add Brenda Names");
					Chemical c = new Chemical(inchi); // sets the inchikey as well
					// ChemicalParser.computeAndSetInchiKey(c);
					db.updateChemicalWithBrenda(c, fields[0]);
					if(cnt%500 == 0) 
						System.out.println("Done with " + cnt);
					cnt++;
				}
			}
			System.out.println("Done addBrendaNames.");
			
			//Close the input stream
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private List<String> readCofactors() {
		System.out.println("reading cofactors");
		List<String> cofactorsl = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(cofactors))));
			String strLine;
			while ((strLine = br.readLine()) != null)   {
				String[] tokens = strLine.split("\t");
				if (tokens[0].trim().equals("cofactor")) {
					cofactorsl.add(tokens[4]);
					System.out.println("IsCofactor = " + tokens[3] + " with SMILES: " + tokens[4]);
				}
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cofactorsl;
	}
	
	/*
	 * TODO: change to adding chemicals with pubchem info (see ChemicalParser)
	 * 		 index on inchikey instead
	 * 		add brenda names after the above
	 */
	public void addChemicals(List<String> cofactors) {
		try {
			/*
			 * INDEX/INDICES created in initIndices()
			db.createChemicalsIndex("InChIKey");
			db.createChemicalsIndex("names.brenda");
			db.createChemicalsIndex("names.pubchem.values");
			db.createChemicalsIndex("names.synonyms");
			*/
			String strLine;
			
			ImportantChemicals imp = new ImportantChemicals();
			BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(imp_chems))));
			//Read the imp chemicals file (DB_SRC DB_ID InChI)
			while ((strLine = br.readLine()) != null) {
				if (strLine.startsWith("#"))
					continue;
				imp.parseAndAdd(strLine);
			}
			br.close();
			System.out.println("");

			int i = 0;
			br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(chemicals))));
			//Read the chemicals list of (name InChI) global list, which may not contain all imp chemicals
			while ((strLine = br.readLine()) != null)   {
				Chemical c = ChemicalParser.parseLine(strLine);
        System.out.println("About to submit: " + c.getInChI());
				imp.setRefs(c);
				if (cofactors.contains(c.getSmiles()))
					c.setAsCofactor();
				System.out.print("Submitted " + (i++) + " " + c.getInChI() + " from " + strLine.split("\\t")[0]);
        System.out.println("\t Slow: Excessive db.getNextAvailableChemicalDBid. Do c++");
				db.submitToActChemicalDB(c, db.getNextAvailableChemicalDBid());
			}
			br.close();
			
			for (Chemical c : imp.remaining()) {
				System.out.print("Submitted important " + (i++));
        System.out.println("\t Slow: Excessive db.getNextAvailableChemicalDBid. Do c++");
				db.submitToActChemicalDB(c, db.getNextAvailableChemicalDBid());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void addCofactorPreComputedAAMs() {
		System.out.println("Installing cofactor pairs.");
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(this.cofactor_pair_AAM))));
			String strLine;
			while ((strLine = br.readLine()) != null)   {
				String[] tokens = strLine.split("\t");
				int id = Integer.parseInt(tokens[0]);
				String mapped_rxn = tokens[1];
				String origin_rxn = tokens[2];
				
				Indigo indigo = new Indigo();
				IndigoObject rr = indigo.loadReaction(mapped_rxn);
				SMILES.renderReaction(rr, "mappedCofactors-" + id + ".png", "Original: " + origin_rxn + " and Mapped:" + mapped_rxn, indigo);
			
				String[] AAMed = mapped_rxn.split(">>");
				String[] origin = origin_rxn.split(">>");
				// System.out.println("Origin: " + origin_rxn);
				List<String> origin_l = Arrays.asList(origin[0].split("[.]"));
				List<String> origin_r = Arrays.asList(origin[1].split("[.]"));
				
				db.submitToCofactorAAM(AAMed[0], AAMed[1], origin_l, origin_r);
				System.out.println("Installed " + mapped_rxn + " for " + origin_l + " -> " + origin_r);
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void tagNatives() {
		System.out.println("reading natives");
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(this.natives))));
			String compound;
			while ((compound = br.readLine()) != null) {
        String inchi = CommandLineRun.consistentInChI(compound, "Tagging natives"); 
				db.updateChemicalAsNative(compound);
      }
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void cleanupChemicalsWithLitminingData() {
		System.out.println("reading litmining chemical inchi cleanup data.");
		try {
			String json = "";
			BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(this.litmining_chem_cleanup))));
			String jsonline = "";
			int count = 0;
			while ((jsonline = br.readLine()) != null) {	
				if (jsonline.trim().equals("====")) {
					Object js = JSON.parse(json);
					if (!(js instanceof BasicDBObject))
						throw new Exception("ERROR: Expected JSON objects separated by '====' in cleanup file; did not find it.");
					BasicDBObject obj = (BasicDBObject) js;
					Integer id = (Integer)obj.get("id");
					// System.out.format("\n[%d] Processing UUID %d\n", count++, id);
					// id is good to know, but lets not use it as it is autogenerated when we run the installer
					// it may change because of the set of chemicals we have to deal with. Instead use current_db_inchi
					
					String correct_inchi = (String)obj.get("badinchi"); 
					String current_db_inchi = (String)obj.get("db_inchi");
        
          // pass this through ConsistentInChI
          correct_inchi = CommandLineRun.consistentInChI(correct_inchi, "Jeff Cleanup"); 
          current_db_inchi = CommandLineRun.consistentInChI(current_db_inchi, "Jeff Cleanup"); 
 
					String synonym = (String)obj.get("name");
					
					/*
					*	// It looks like jeff's list is created from a roundtrip calculation. 
					*	// So the code below can be removed... 
					*	String correct_inchi_rt = CommandLineRun.consistentInChI(correct_inchi);
					*	if (!correct_inchi_rt.equals(correct_inchi))
					*		System.err.println("[WARNING] *** At least one inchi has rt different.");
					*	if (correct_inchi_rt.equals(current_db_inchi)) {
					*		System.err.format("[WARNING] *** tell jeff ***\n");
					*		System.err.format("[WARNING] *** jeff's suggestion = " + correct_inchi);
					*		System.err.format("[WARNING] *** but rt through indigo = " + correct_inchi_rt);
					*		System.err.format("[WARNING] *** which is the same as the current DB inchi = " + current_db_inchi);
					*	} else {
					*/
					String correct_inchi_rt = correct_inchi;
					
					{
						// lookup entry where InChI = "current_db_inchi"
						// we need to remove the "synonym" from this entry
						// retrieve entry with InChI = "correct_inchi" (or create if it doesn't exist)
						// set the new entry's synonym to "synonym"
						long idfrom = db.removeSynonym(current_db_inchi, synonym);
						long idto = db.updateOrCreateWithSynonym(correct_inchi_rt, synonym);
						System.out.format("Moved from [%d] to [%d] the synonym: %s\n", idfrom, idto, synonym);
					}
					json = "";
				} else {
					json += jsonline + " ";
				}
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addReactions() {
		EcClass.db = db;
		EcClass.missingChems = missingChems;
		EcClass.missingOrgs = missingOrgs;

		/*
		 * INDEX/INDICES created in initIndices()
		db.createOrganismNamesIndex("name");
		*/
		
		FileInputStream fis;
		try
        {
            fis = new FileInputStream(brenda);
        }
        catch (FileNotFoundException e)
        {
            System.out.println("BRENDAPARSER: File " + brenda +
                               " not found.");
            return;
        }

        BrendaParser newParser = new BrendaParser(fis);
        try {
			newParser.Database();
		} catch (ParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		try {
			if(chem!=null) {
				BufferedWriter chemWriter = new BufferedWriter(chem);
				for(String s : missingChems) {
					chemWriter.write(s + "\n");
				}
				chemWriter.close();
			}
			if(org!=null) {
				BufferedWriter orgWriter = new BufferedWriter(org);
				for(String s : missingOrgs) {
					orgWriter.write(s + "\n");
				}
				orgWriter.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private void addReactionSimilarity() {
		System.err.println("=================== WARNING ===================");
		System.err.println("==== reaction similarity not implemented =====");
		System.err.println("=================== WARNING ===================");
	}

	private void addChemicalSimilarity() {
		Indigo indigo = new Indigo();
		IndigoInchi inchi = new IndigoInchi(indigo);
		this.db.addSimilarityBetweenAllChemicalsToDB(indigo, inchi);
	}
	
	public void writeErrors(FileWriter chemFW, FileWriter orgFW) {
		this.chem = chemFW;
		this.org = orgFW;
	}
	
	/*
	 * args should contain the following in:
	 * data directory relative to working
	 * brenda data
	 * organisms data
	 * names data
	 * chemicals (pubchem)
	 * brenda names
	 * 
	 * 
	 * optionally:
	 * filename to put unfound chemicals
	 * filename to put unfound organisms
	 */
	public static void main(String[] args){
    	Indigo ind_makesure = new Indigo();
    	IndigoInchi ic_makesure = new IndigoInchi(ind_makesure);
		// for(String a : args)
		//	System.out.println(a);

		String operation = args[0];
		int dbPort = Integer.parseInt(args[1]);
		String server = args[2];
		String dbname = args[3];
		
		if (operation.equals("BRENDA")) {
			String unfoundChemNames = null, unfoundOrgNames = null;
			
			String path = System.getProperty("user.dir")+"/"+args[4];
			String brendafile = args[5];
			String taxonomy = args[6];
			String organismNames = args[7];
			String chemicals = args[8]; 
			String brendaNames = args[9];
			String cofactors = args[10];
			String cofactor_pair_AAM = args[11];
			String natives = args[12];
			String litmining_chem_cleanup = args[13];
			String imp_chemicals = args[14];
			
			if (args.length > 15) {
				unfoundChemNames = args[15];
				unfoundOrgNames = args[16];
			}
		
			Main installer = new Main(brendafile,taxonomy,organismNames,chemicals,brendaNames,cofactors, cofactor_pair_AAM, natives, litmining_chem_cleanup, imp_chemicals, path, server, dbPort, dbname);
			Long s = System.currentTimeMillis();

			boolean add_org = true, 
					add_chem = true, 
					add_brenda_names = true, 
					add_cofactor_AAMs = true, 
					add_natives = true,
					add_litmining_chem_cleanup = true,
					add_brenda_reactions = true,
					add_chem_similarity = false,
					add_rxn_similarity = false;

			if (!add_org) { System.out.println("SKIPPING organisms"); } else {
				System.out.println("inserting organisms");
				installer.addOrganisms();
			}
			System.out.println((System.currentTimeMillis() - s)/1000);
			
			if (!add_chem) { System.out.println("SKIPPING chemicals"); } else {
				System.out.println("inserting chemicals");
				installer.addChemicals(installer.readCofactors());
			}
			System.out.println((System.currentTimeMillis() - s)/1000);

			if (!add_brenda_names) { System.out.println("SKIPPING brenda names"); } else {
				System.out.println("inserting brenda names");
				installer.addBrendaNames();
			}
			System.out.println((System.currentTimeMillis() - s)/1000);

			if (!add_cofactor_AAMs) { System.out.println("SKIPPING cofactor AAMs"); } else {
				System.out.println("inserting precomputed cofactor AAM pairs");
				installer.addCofactorPreComputedAAMs();
			}
			System.out.println((System.currentTimeMillis() - s)/1000);
			
			if(unfoundChemNames != null) {
				File c = new File(unfoundChemNames);
				File o = new File(unfoundOrgNames);
				try {
					c.createNewFile();
					o.createNewFile();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				
				try {
					installer.writeErrors(new FileWriter(unfoundChemNames),new FileWriter(unfoundOrgNames));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (!add_brenda_reactions) { System.out.println("SKIPPING reactions"); } else {
				System.out.println("inserting reactions");
				installer.addReactions();
			}
			
			if (!add_natives) { System.out.println("SKIPPING natives tagging."); } else {
				System.out.println("tagging native chemicals");
				installer.tagNatives();
			}

			if (!add_litmining_chem_cleanup) { System.out.println("SKIPPING cleanup of chemicals using litmining data."); } else {
				System.out.println("cleaning chemicals based on litmining deconvolving data.");
				installer.cleanupChemicalsWithLitminingData();
			}
			
			/* this would take 36 days to finish! 32000*32000 entries to add, so not computed */
			if (!add_chem_similarity) { System.out.println("SKIPPING similarity computation between chemicals."); } else {
				System.out.println("inserting chemical similarity");
				installer.addChemicalSimilarity();
			}
			
			if (!add_rxn_similarity) { System.out.println("SKIPPING similarity computation between reactions."); } else {
				System.out.println("inserting reaction similarity");
				installer.addReactionSimilarity();
			}
			System.out.println((System.currentTimeMillis() - s)/1000);
			
			//EcClass.printNumOrgsSeen();
			
		} else if (args[0].equals("PUBMED")) {
			String pubmedDir = args[4];
			int start = Integer.parseInt(args[5]);
			int end = Integer.parseInt(args[6]);
			PubmedDBCreator pmInstall = new PubmedDBCreator(pubmedDir, start, end, server, dbPort, dbname);
			pmInstall.addPubmedEntries();
		
		} else if (args[0].equals("RARITY")) {
			long start = Long.parseLong(args[4]);
			long end = Long.parseLong(args[5]);
			Rarity rarity = new Rarity(start, end, server, dbPort, dbname);	
			rarity.installRarityMetrics();
			
		} else if (args[0].equals("KEGG")) {
			MongoDB db = new MongoDB(server, dbPort, dbname);
			String path = System.getProperty("user.dir")+"/"+args[4];
			KeggParser.parseKegg(path + "/reaction.lst", path + "/compound.inchi", path + "/compound", path + "/reaction", path + "/cofactors.txt", db);

		} else if (args[0].equals("BALANCE")) {
			MongoDB db = new MongoDB(server, dbPort, dbname);
			BalanceEquations.balanceAll(db, true, null, 41852L);
			BalanceEquations.balanceAll(db, false, 41851L, null);

		} else if (args[0].equals("ENERGY")) {
			MongoDB db = new MongoDB(server, dbPort, dbname);
			EstimateEnergies.estimateForChemicals(db);
			EstimateEnergies.estimateForReactions(db);

		} else if (args[0].equals("SWISSPROT")) {
			String path = System.getProperty("user.dir")+"/"+args[4];
      int nfiles = SwissProt.getDataFileNames(path).size();
      int chunk = 1;
      for (int i=0; i<nfiles; i+=chunk) {
        MongoDB db = new MongoDB(server, dbPort, dbname);
			  SwissProt s = new SwissProt(path);
        s.process(i, i+chunk);          // process the chunk
        s.sendToDB(db);                 // install in DB
        db.close();
      }
      
		} else if (args[0].equals("MAP_SEQ")) {
      MongoDB db = new MongoDB(server, dbPort, dbname);

      SeqIdentMapper mapper = new SeqIdentMapper(db);
      // this maps rxnid (db.actfamilies) -> { seqid } (db.seq)
      // and creates the rev links seqid -> { rxnid } as well
      // additionally it might add more entries to db.seq through
      // web api lookup for accession numbers that are not 
      // installed as part of the above SWISSPROT install. E.g.,  
      // Some BRENDA acc#'s refer to GenBank, unreviewed 
      // Uni/SwissProt (i.e., TrEBML, EMBL)
      // It also calls the NCBI Entrez API using biopython
      // to lookup sequences by their EC# + Organism
      mapper.map();

		} else if (args[0].equals("VENDORS")) {
			String vendors_file = System.getProperty("user.dir")+"/"+args[4];
      Set<String> priority_chems_files = new HashSet<String>();
      // assume the rest of the args are priority chem 
      // files, i.e., list of inchis, e.g., reachables etc.
      for (int i=5; i<args.length; i++)
        priority_chems_files.add(args[i]);
      MongoDB db = new MongoDB(server, dbPort, dbname);
      new ChemSpider().addChemVendors(db, vendors_file, priority_chems_files);
      db.close();

		} else if (args[0].equals("FTO")) {
			String vendors_file = System.getProperty("user.dir")+"/"+args[4];
      Set<String> priority_chems_files = new HashSet<String>();
      // assume the rest of the args are priority chem 
      // files, i.e., list of inchis, e.g., reachables etc.
      for (int i=5; i<args.length; i++)
        priority_chems_files.add(args[i]);
      MongoDB db = new MongoDB(server, dbPort, dbname);
      new FTO().addPatents(db, vendors_file, priority_chems_files);
      db.close();

		} else if (args[0].equals("INFER_SAR")) {
      MongoDB db = new MongoDB(server, dbPort, dbname);
      SARInfer sar_infer = new SARInfer(db);
			if (args.length <= 4) {
        // no accessions provided; infer SAR for all
        sar_infer.infer();
      } else {
        // some accessions provided; infer SAR only for those
        List<String> accessions = new ArrayList<String>();
        for (int i = 4; i < args.length; i++)
          accessions.add(args[i]);
        sar_infer.infer(accessions);
      }

		} else if (args[0].equals("KEYWORDS")) {
      MongoDB db = new MongoDB(server, dbPort, dbname);

      QueryKeywords miner = new QueryKeywords(db);
      miner.mine_all();

		} else if (args[0].equals("METACYC")) {
			String path = System.getProperty("user.dir")+"/"+args[4];
			int start = Integer.parseInt(args[5]);
			int end = Integer.parseInt(args[6]);

      // Note: by default, we only process Tier1, and Tier2 files from metacyc
      // They are the ones that are manually curated, and there are 38 of them.
      // (Tier3 is not: http://biocyc.org/biocyc-pgdb-list.shtml)
      // But if you still want to process the additional 3487 Tier3 files
      // Then add flags to say we dont just want to process Tier1,2:
      //      - int nfiles = MetaCyc.getOWLs(path, false)
      //      - MetaCyc m = new MetaCyc(path, false)

      int nfiles = MetaCyc.getOWLs(path).size();
      System.out.println("Total: " + nfiles + " level3 biopax files found.");
      System.out.println("Range: [" + start + ", " + end + ")");
      int chunk = 1; // you can go up to a max of about 20 chunks (mem:3gb)
      // see "Performance" section below for a run over 100 files
      for (int i=start; i<nfiles && i<end; i+=chunk) {
        MongoDB db = new MongoDB(server, dbPort, dbname);
			  MetaCyc m = new MetaCyc(path);  // important: create a new MetaCyc object
                                        // for each chunk coz it holds the entire
                                        // processed information in a HashMap of
                                        // OrganismCompositions.
        System.out.format("Processing: [%d, %d)\n", i, i+chunk);
        m.process(i, i+chunk);          // process the chunk
        m.sendToDB(db);                 // install in DB
        db.close();
        
        // when iterating to new chunk, MetaCyc object will be GC'ed releasing
        // accumulated OrganismCompositions information for those organisms
        // but that is ok, since we already installed it in MongoDB.
      }
      
      // Testing:
      // List<String> files = new ArrayList<String>();
      // files.add("ecol679205-hmpcyc/biopax-level3.owl");
      // m.process(files);
      // m.get("ecol679205-hmpcyc/biopax-level3.owl").test_szes_ecol679205_hmpcyc();

      // Performance: 
      // int start =  1120; // 0; // 1120 is ecocyc
      // int end   =  1220; // Integer.MAX_VALUE; // Integer.MAX_VALUE;
      // Time: 1861s [1120,1220) @ 1/chunk -- therefore ~18 hours to do 3528
      // > print(db.chemicals.count()); print(db.actfamilies.count()); print(db.sequences.count())
      // 67543,  50809,  24329 -- old   chems, rxns, sequences
      // 95190, 232550, 206037 -- new   chems, rxns, sequences
      // 27647, 181741, 181708 -- delta chems, rxns, sequences
      // only 1271 chems are really small molecules with new inchis. rest big molecules
      // so wont appear in reachables search. All big molecules will be by default 
      // unreachable in this setting; but we could make them reachable!?
      //
      // Resulting DB size:
      // 0.42, 0.28, 0.15 -- new chems, rxns, sequences db size in GB
      // So expected total size: (above * 35.28)
      // 14.82, 9.88, 3.80 -- sum = 27.5 GB

		} else {
			System.err.format("First argument needs to be BRENDA, RARITY, PUBMED, KEGG, or METACYC. Aborting. [Given: %s]\n", args[0]);
		}
	}
}
