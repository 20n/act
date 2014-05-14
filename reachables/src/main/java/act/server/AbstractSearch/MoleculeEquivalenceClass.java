package act.server.AbstractSearch;

import java.util.ArrayList;

import act.shared.Chemical;

public class MoleculeEquivalenceClass {
	public ArrayList<Long> matchingChemicalUUIDs;

	public MoleculeEquivalenceClass(){
		this.matchingChemicalUUIDs = new ArrayList<Long>();
	}
	
	public void addChemical(Long c){
		this.matchingChemicalUUIDs.add(c);
	}
	
	public String uuidListString(){
		String str = "";
		for (Long c : matchingChemicalUUIDs){
			str+="\t"+c;
		}
		return str;
	}
}
