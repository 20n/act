package act.server.Search;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;

import com.google.gwt.dev.util.Pair;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class AllPairs {
	Set<Pair<Long, Long>> reactions;
	HashSet<Long> allCompounds;
	HashMap<Long, Short> compToIndex;
	HashMap<Short, Long> indexToComp;
	byte[][] allPairs; //assuming no path length is > 128
	//short[][] next;
	
	short maxIndex = 0;

	MongoDB db;
	
	private boolean[][] allPairsReachable; //unused

	public AllPairs() {
		db =new MongoDB();
	}
	
	/*
	 * Returns set of compounds.
	 */
	public Set<Long> getCompounds() {
		return compToIndex.keySet();
	}
	
	public boolean hasPath(Long s, Long t) {
		if(allPairsReachable!=null)
			return allPairsReachable[compToIndex.get(s)][compToIndex.get(t)];
		if(allPairs!=null)
			return allPairs[compToIndex.get(s)][compToIndex.get(t)] >= 0;
		System.err.println("AllPairs: have not ran floyd yet");
		return false;
	}
	
	public byte pathLength(Long s, Long t) {
		return allPairs[compToIndex.get(s)][compToIndex.get(t)];
	}
	
	public List<Long> getPath() {
		return null;
	}
	
	public void getAllReactions() {
		getAllReactions(-1);
	}
	
	/*
	 * So we don't have to keep querying db for different organisms
	 */
	private HashMap<Long,Set<Pair<Long,Long>>> rxnsByOrganism;
	
	private void initReactions() {
		rxnsByOrganism = new HashMap<Long,Set<Pair<Long,Long>>>();
		rxnsByOrganism.put(new Long(-1), new HashSet<Pair<Long,Long>>()); //all rxns
		DBIterator iterator = db.getIteratorOverReactions(new BasicDBObject(), false /* notimeout=false */, null);
		int count = 0;
		System.out.print("getAllReactions: starting");
		while (iterator.hasNext()) {
			DBObject r = iterator.next();
			
			BasicDBList orgs = (BasicDBList) r.get("organisms");
			Pair<Long, Long> rxn = getRarestPair(r);
			
			for(Object o : orgs) {
				Long tempID = (Long)((DBObject) o).get("id");
				if(!rxnsByOrganism.containsKey(tempID))
					rxnsByOrganism.put(tempID, new HashSet<Pair<Long,Long>>());
				rxnsByOrganism.get(tempID).add(rxn);
				
			}
			rxnsByOrganism.get(new Long(-1)).add(rxn);
			
			count++;
			if (count % 500 == 0) {
				System.out.print(".");
			}
		}
		System.out.println("\ngetAllReactions: done");
	}
	
	/*
	 * Initializes reactions and allCompounds
	 */
	public void getAllReactions(long orgID) {
		//MongoDB db = new MongoDB("hz.cs.berkeley.edu", 27017, "actv01");
		if(rxnsByOrganism==null)
			initReactions();
		reactions = rxnsByOrganism.get(orgID);
		allCompounds = new HashSet<Long>();
		for(Pair<Long,Long> rxn: reactions) {
			allCompounds.add(rxn.left);
			allCompounds.add(rxn.right);
		}
		System.out.println(reactions.size());
	}

	/*
	 * Returns the rarest substrate and product for a reaction, so that
	 * reactions are one to one.
	 */
	public Pair<Long, Long> getRarestPair(DBObject rxn) {
		BasicDBList products = (BasicDBList) ((DBObject) rxn.get("enz_summary"))
				.get("products");
		BasicDBList substrates = (BasicDBList) ((DBObject) rxn
				.get("enz_summary")).get("substrates");

		Double minProdValue = new Double(10000);
		Long minProduct = (long) -1;
		for (int i = 0; i < products.size(); i++) {
			DBObject compound = (DBObject) products.get(i);
			Double rarity = (Double) compound.get("rarity");
			if (rarity < minProdValue) {
				minProdValue = rarity;
				minProduct = (Long) compound.get("pubchem");
			}
		}

		Double minSubsValue = new Double(10000);
		Long minSubstrate = new Long(-1);
		for (int i = 0; i < substrates.size(); i++) {
			DBObject compound = (DBObject) substrates.get(i);
			Double rarity = (Double) compound.get("rarity");
			if (rarity < minSubsValue) {
				minSubsValue = rarity;
				minSubstrate = (Long) compound.get("pubchem");
			}
		}
		

		return Pair.create(minSubstrate,minProduct);
	}

	/*
	 * Create compound index. Run after getAllReactions.
	 */
	public void initialize() {
		compToIndex = new HashMap<Long, Short>();
		indexToComp = new HashMap<Short, Long>();
		
		Iterator<Long> itr = allCompounds.iterator();
		short index = 0;
		while (itr.hasNext()) {
			Long comp = itr.next();
			compToIndex.put(comp, index);
			indexToComp.put(index, comp);
			index++;
		}
		maxIndex = index;
		System.out.println("initialize: got reactions " + reactions.size());
		System.out.println("initialize: got compounds " + allCompounds.size());
	}

	/*
	 * Finds the reachability between allPairs, updates allPairs.
	 * Unused right now.
	 * Used if memory is issue and the actual distances do not matter.
	 */
	public void runFloydReachability() {
		allPairsReachable = new boolean[maxIndex][maxIndex];
		System.out.println("runFloydReachability: starting");
		boolean[][] lastPairs = new boolean[maxIndex][maxIndex];
		System.out.print("runFloydReacability: initializing");
		long counter = 0;
		// initialize allPairs
		for (short i = 0; i < maxIndex; i++) {
			for (short j = 0; j < maxIndex; j++) {
				if (i == j)
					allPairsReachable[i][j] = true;
				counter++;
				if (counter % 5000000 == 0) {
					System.out.print(".");
				}
			}
		}
		System.out.println("");
		System.out.println("runFloydReachable: initializing from reactions");
		for (Pair<Long, Long> rxn : reactions) {
			short i = compToIndex.get(rxn.left);
			short j = compToIndex.get(rxn.right);
			allPairsReachable[i][j] = true;
		}
		System.out.println("runFloydReachability: creating lastPairs");
		
		lastPairs = allPairsReachable;
		System.out.println("runFloyd: starting main loop");
		long start = System.currentTimeMillis();
		long segment = start;
		for (short k = 0; k < maxIndex; k++) {
			for (short i = 0; i < maxIndex; i++) {
				if (!lastPairs[i][k]) continue;
				for (short j = 0; j < maxIndex; j++) {
					if (lastPairs[k][j])
						allPairsReachable[i][j] = true;
				}
			}
			if (k % 500 == 0) {
				long elapsedTimeMillis = System.currentTimeMillis() - segment;
				float elapsedTimeSec = elapsedTimeMillis / 1000F;
				segment = System.currentTimeMillis();
				System.out.println("runFloyd: " + k + "/" + maxIndex + " in "
						+ elapsedTimeSec + "sec");
			}

		}
		System.out.println("runFloydReachability: finished in "
				+ (System.currentTimeMillis() - start) / 60000F + " min.");
	}

	
	
	/*
	 * Finds the shortest distance between allPairs, updates allPairs.
	 */
	public byte[][] runFloyd() {
		allPairs = new byte[maxIndex][maxIndex];
		//next = new short[maxIndex][maxIndex];
		System.out.println("runFloyd: starting");
		byte[][] lastPairs;// = new byte[maxIndex][maxIndex];
		System.out.print("runFloyd: initializing to max");
		long counter = 0;
		// initialize allPairs
		for (short i = 0; i < maxIndex; i++) {
			for (short j = 0; j < maxIndex; j++) {
				if (i != j) {
					allPairs[i][j] = -1;
					//next[i][j] = -1;
				}
				counter++;
				if (counter % 5000000 == 0) {
					System.out.print(".");
				}
			}
		}
		System.out.println("");
		System.out.println("runFloyd: initializing from reactions");
		for (Pair<Long, Long> rxn : reactions) {
			short i = compToIndex.get(rxn.left);
			short j = compToIndex.get(rxn.right);
			if(allPairs[i][j] < 0) {
				allPairs[i][j] = 1;
				//next[i][j] = -1;
			}
		}
		System.out.println("runFloyd: creating lastPairs");
		//for (int i = 0; i < maxIndex; i++) {
			//lastPairs[i] = allPairs[i].clone();
		//}
		lastPairs = allPairs;
		System.out.println("runFloyd: starting main loop");
		long start = System.currentTimeMillis();
		long segment = start;
		for (short k = 0; k < maxIndex; k++) {
			for (short i = 0; i < maxIndex; i++) {
				byte ik = lastPairs[i][k];
				if (ik < 0)
					continue;
				for (short j = 0; j < maxIndex; j++) {
					short kj = lastPairs[k][j];
					if (kj < 0)
						continue;
					byte ij = lastPairs[i][j];
					if (ij < 0) {
						allPairs[i][j] = (byte) (ik + kj);
						//next[i][j] = k;
					} else {
						if (ik+kj < ij) {
							allPairs[i][j] = (byte) (ik+kj);
							//next[i][j] = k;
						}
					}
				}
			}
			//for (int i = 0; i < maxIndex; i++) {
				//System.arraycopy(lastPairs[i], 0, allPairs[i], 0, maxIndex);
				
				//for (int j = 0; j < maxIndex; j++)
					//lastPairs[i][j] = allPairs[i][j];
			//}

			if (k % 500 == 0) {
				long elapsedTimeMillis = System.currentTimeMillis() - segment;
				float elapsedTimeSec = elapsedTimeMillis / 1000F;
				segment = System.currentTimeMillis();
				System.out.println("runFloyd: " + k + "/" + maxIndex + " in "
						+ elapsedTimeSec + "sec");
			}

		}
		System.out.println("runFloyd: finished in "
				+ (System.currentTimeMillis() - start) / 60000F + " min.");
		return allPairs;
	}

	public boolean verifyPair(short a, short b) {
		return true;
	}

	public void driver() {
		System.out.println("Getting AllPairs");
		AllPairs pairs = new AllPairs();
		pairs.getAllReactions();
		pairs.initialize();
		pairs.runFloydReachability();
		if(pairs!=null) return;
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		FileOutputStream fos2 = null;
		ObjectOutputStream oos2 = null;
		FileOutputStream fos3 = null;
		ObjectOutputStream oos3 = null;
		try {
			fos = new FileOutputStream("indexToComp.dat");
			oos = new ObjectOutputStream(fos);
			oos.writeObject(pairs.indexToComp);
			oos.flush();
			fos2 = new FileOutputStream("allPairs.dat");
			oos2 = new ObjectOutputStream(fos2);
			oos2.writeObject(pairs.allPairs);
			oos2.flush();
			fos3 = new FileOutputStream("compToIndex.dat");
			oos3 = new ObjectOutputStream(fos3);
			oos3.writeObject(pairs.compToIndex);
			oos3.flush();
		} catch (Exception e) {
		} finally {
			try {
				fos.close();
				oos.close();
				fos2.close();
				oos2.close();
				fos3.close();
				oos3.close();
			} catch (IOException e) {

			}
		}
	}

	@SuppressWarnings("unchecked")
	public void loadData() throws IOException {
		System.out.println("Loading Data");
		FileInputStream fis = null;
		ObjectInputStream ois = null;
		FileInputStream fis2 = null;
		ObjectInputStream ois2 = null;
		FileInputStream fis3 = null;
		ObjectInputStream ois3 = null;
		try {
			fis = new FileInputStream("allPairs.dat");
			ois = new ObjectInputStream(fis);
			allPairs = (byte[][]) ois.readObject();
			System.out.println("finished reading allPairs.dat");
			fis2 = new FileInputStream("indexToComp.dat");
			ois2 = new ObjectInputStream(fis2);
			indexToComp = (HashMap<Short, Long>) ois2.readObject();
			System.out.println("finished reading indexToComp.dat");
			fis3 = new FileInputStream("compToIndex.dat");
			ois3 = new ObjectInputStream(fis3);
			compToIndex = (HashMap<Long, Short>) ois3.readObject();
			System.out.println("finished reading compToIndex.dat");
		} catch (Exception e) {
			System.out.println("Exception: " + e);
		} finally {
			fis.close();
			ois.close();
			fis2.close();
			ois2.close();
			fis3.close();
			ois3.close();
		}
		System.out.println("Finished Loading Data");
	}

	public short bfs(short s, short t) { //closed set needed in case of cycles?
		System.out.println(s + "-" + t);
		short dist = 0;
		Queue<Short> q = new LinkedList<Short>();
		Pair<Long, Long> rxn = null;
		q.add(s);
		q.add((short) -1);
		while (!q.isEmpty()) {
			short comp = q.remove();
			if (comp == -1) {
				dist++;
				q.add((short) -1);
				continue;
			}
			for (short i = 0; i < maxIndex; i++) {
				if (comp == t) {
					return dist;
				}
				rxn = Pair.create(indexToComp.get(comp), indexToComp.get(i));
				if (reactions.contains(rxn)) {
					q.add(i);
				}
			}
		}
		return dist;
	}

	public void tester() {
		System.out.println("Testing AllPairs");
		try {
			loadData();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		// for (short k = 0; k < 10; k++) {
		// for (short i = 0; i < 10; i++) {
		// System.out.print(" " + allPairs[k][i]);
		// }
		// System.out.println("");
		// }
		getAllReactions();
		maxIndex = (short) compToIndex.size();
		int correct = 0;
		int total = 0;
		for (short i = 0; i < maxIndex; i++) {
			for (short j = 0; j < maxIndex; j++) {
				if (allPairs[i][j] != -1) {
					short bfsDist = bfs(i, j);
					if (bfsDist != allPairs[i][j]) {
						System.out.println("*wrong\t" + i + "=>" + j);
						System.out.println("*vals\tallPairs=" + allPairs[i][j]
								+ "\tbfs=" + bfsDist);
					} else {
						System.out.println("correct\t" + i + "=>" + j);
						correct++;
					}
					total++;
					System.out.println("");
					System.out.flush();
				}
			}
		}

		System.out.println("RESULTS: correct=" + correct + " total=" + total);
	}

	public static void main(String[] args) {
		AllPairs pairs = new AllPairs();
		//pairs.driver();
		pairs.getAllReactions();
		//pairs.initialize();
		//pairs.runFloyd();
		//pairs.tester();
	}
}
