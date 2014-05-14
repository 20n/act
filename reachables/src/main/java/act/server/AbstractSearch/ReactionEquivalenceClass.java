package act.server.AbstractSearch;

import java.util.ArrayList;

import act.server.EnumPath.OperatorSet;
import act.shared.Chemical;
import act.shared.Reaction;

public class ReactionEquivalenceClass {
	ArrayList<Long> matchingReactionUUIDs;

	public ReactionEquivalenceClass(){
		this.matchingReactionUUIDs = new ArrayList<Long>();
	}
	
	public void addReaction(Long r){
		this.matchingReactionUUIDs.add(r);
	}
	
	public ArrayList<Long> getMatchingReactions(){
		return this.matchingReactionUUIDs;
	}
	
	public String toString(){
		return "size: "+this.matchingReactionUUIDs.size();
	}
	
	public String uuidListString(){
		String str = "";
		for (Long r : matchingReactionUUIDs){
			str+="\t"+r;
		}
		return str;
	}
}
