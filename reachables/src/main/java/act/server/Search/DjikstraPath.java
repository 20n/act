package act.server.Search;

import java.util.HashSet;
import java.util.Set;

public class DjikstraPath implements Comparable<DjikstraPath> {
	private int cost;
	private Set<Long> reactions;
	private Set<Long> chemicalsBeforeLastReaction;
	
	public DjikstraPath(int cost, Set<Long> path) {
		this.cost = cost;
		reactions = new HashSet<Long>(path);
	}
	
	public int getCost() { return cost; }
	public void setCost(int cost) { this.cost = cost; }
	
	
	public Set<Long> getChemicalsBeforeLastReaction() { return chemicalsBeforeLastReaction; }
	public void setChemicalsBeforeLastReaction(Set<Long> products) { this.chemicalsBeforeLastReaction = products; }
	
	
	@Override
	public int compareTo(DjikstraPath arg0) {
		if (this.cost < arg0.cost) return -1;
		else if (this.cost == arg0.cost) return 0;
		return 1;
	}
	
	@Override
	public int hashCode() {
		return getReactions().hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		DjikstraPath other = (DjikstraPath) o;
		return getReactions().equals(other.getReactions());
	}

	public Set<Long> getReactions() {
		return new HashSet<Long>(reactions);
	}
	
}
