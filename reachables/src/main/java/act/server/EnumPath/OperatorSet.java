package act.server.EnumPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.server.Molecules.BRO;
import act.server.Molecules.CRO;
import act.server.Molecules.ERO;
import act.server.Molecules.RO;
import act.server.Molecules.RxnWithWildCards;
import act.server.Molecules.TheoryROs;
import act.server.SQLInterface.MongoDB;
import act.shared.helpers.P;



public class OperatorSet {
	public class OpID { 
		int id; 
		public OpID(int i) { this.id = i; }
		@Override
		public String toString() { return "" + this.id; }
		@Override
		public boolean equals(Object o) { if (!(o instanceof OpID)) return false; return this.id == ((OpID)o).id; }
		@Override
		public int hashCode() { return this.id; }
	}

	MongoDB DB;
	HashMap<OpID, CRO> cros;
	HashMap<String, List<ERO>> eros; // the indexing key is the CRO SMARTS because of reason (##) below
	
	/* reason (##) for indexing on String croSMARTS as opposed to CRO:
	 * 
	 * note that we need to key on the queryrxnstring instead of the cro because equality/hash on a cro are 
	 * direction agnostic so if we don't key on the internal string then opposite facing cros and eros get 
	 * bunched into one, and then when we later, filter by num of reactants, it gets all screwy
	*/
	
	public HashMap<OpID, CRO> getAllCROs() { 
		return this.cros;
	}
	
	public List<ERO> getAllEROsFor(CRO cro) {
		return this.eros.get(cro.rxn());
	}
	
	public OperatorSet(MongoDB db) {
		this.DB = db;
		this.cros = new HashMap<OpID, CRO>();
		this.eros = new HashMap<String, List<ERO>>();
	}
	
	public OperatorSet(MongoDB db, int numOps, boolean filterDuplicates) {
		this.DB = db;
		this.cros = new HashMap<OpID, CRO>();
		this.eros = new HashMap<String, List<ERO>>();
		readFromDB(numOps, null, filterDuplicates); // returns at most 2*numOps from DB (rxn and its reverse for each lookup)
		System.err.format("Read %d CRO and their ERO operators from DB.\n", this.cros.size());
	}
	
	public OperatorSet(MongoDB db, int numOps, List<Integer> opsWhitelist, boolean filterDuplicates) {
		this.DB = db;
		this.cros = new HashMap<OpID, CRO>();
		this.eros = new HashMap<String, List<ERO>>();
		readFromDB(numOps, opsWhitelist, filterDuplicates); // returns at most 2*numOps from DB (rxn and its reverse for each lookup)
		System.err.format("Read %d CRO and their ERO operators from DB.\n", this.cros.size());
	}
	
	public CRO lookupCRO(OpID id) {
		return this.cros.get(id);
	}

	private void readFromDB(int numOps, List<Integer> opsWhitelist, boolean filterDuplicates) {
		List<RO> ops = new ArrayList<RO>();
		if (filterDuplicates)
			System.out.println("Filterduplicates NOT SUPPORTED!");

		int i=0;
		
		HashMap<String, OpID> croIDs = new HashMap<String, OpID>(); // See reason (##) above for why we index on CRO SMARTS
		for (P<Integer, TheoryROs> tro : this.DB.getOperators(numOps, opsWhitelist)) {
			System.out.format("DBOperator[%d] = %s\n", tro.fst(), tro.snd());
			TheoryROs t = tro.snd();
			CRO cro, croRev;
			ERO ero, eroRev;
			
			cro = t.CRO();
			if (malformed(cro)) // check if the substrates or products are empty
				continue;
			ero = t.ERO();
			if (malformed(ero)) // check if the substrates or products are empty
				continue;
			croRev = cro.reverse();
			eroRev = ero.reverse();
			i = addToOpSet(i, cro, ero, croIDs);
			i = addToOpSet(i, croRev, eroRev, croIDs);
		}
	}

	public int addToOpSet(int i, CRO cro, ERO ero, HashMap<String, OpID> croIDs) {
		OpID opid; int reti;
		String croSMARTS = cro.rxn();
		if (croIDs.containsKey(croSMARTS)) {
			opid = croIDs.get(croSMARTS);
			reti = i;
		} else {
			opid = new OpID(i);
			this.cros.put(opid, cro);
			reti = i+1;
		}
			
		if (!this.eros.containsKey(croSMARTS))
			this.eros.put(croSMARTS, new ArrayList<ERO>());
		this.eros.get(croSMARTS).add(ero);
		System.out.format("Operator[%s] = %s :> %s\n", opid, cro, ero);
		
		return reti; // either incremented (ie., i+1) if we created a new ID, or the same if we did not use from the pool of i's.
	}
	
	public void addToOpSet(OpID opid, CRO cro, List<ERO> ero) {
		String croSMARTS = cro.rxn();
		this.cros.put(opid, cro);
		if (!this.eros.containsKey(croSMARTS))
			this.eros.put(croSMARTS, new ArrayList<ERO>());
		this.eros.get(croSMARTS).addAll(ero);
		System.out.format("Operator[%s] = %s :> %s\n", opid, cro, ero);
	}

	private boolean malformed(RO ro) {
		String roStr = ro.rxn().trim();
		if (roStr.startsWith(">>") || roStr.endsWith(">>"))
			return true; // produces atoms out of thin air; or poofs atoms.. 
		return false; // ok operator
	}

	private RO extractROwithAppropriateClass(TheoryROs tro, Class cls) { 
		RO ro;
		if (cls.equals(CRO.class)) {
			ro = tro.CRO();
		} else if (cls.equals(ERO.class)) {
			ro = tro.ERO();
		} else if (cls.equals(BRO.class)) {
			ro = tro.BRO();
		} else {
			ro = null;
			System.err.println("Invalid operator asked for.");
			System.exit(-1);
		}
		return ro;
	}

	public P<CRO, OpID> getNext(List<OpID> alreadyAppliedOps) {
		for (OpID id : this.cros.keySet()) {
			if (alreadyAppliedOps.contains(id))
				continue;
			return new P<CRO, OpID>(this.cros.get(id), id);
		}
		return null; // no found
	}

	public boolean coveredAll(List<OpID> alreadyAppliedOps) {
		for (OpID id : this.cros.keySet())
			if (!alreadyAppliedOps.contains(id))
				return false;
		return true;
	}
}
