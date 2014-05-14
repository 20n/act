package act.server.Search;

import java.util.Set;

/**
 * Used by priority queue in AbstractHypergraphRanker
 * 
 * This sorts queue by number of unexpanded parents. 
 * For a Djikstra-like algorithm, see DjikstraRankingNode.
 */
public abstract class RankingNode implements Comparable<RankingNode> {
	protected Integer numUnexpandedParents;
	protected Long id;
	
	public abstract Set<Integer> getCosts();
	public abstract void setCosts(Set<Integer> costs);
	
	public Long getID() { return id; }
	
	public void decrementParent() { this.numUnexpandedParents--; }
	
	@Override
	public int compareTo(RankingNode other) {
		return this.numUnexpandedParents.compareTo(other.numUnexpandedParents);
	}
	
	@Override
	public boolean equals(Object obj) {
		RankingNode other = (RankingNode) obj;
		return other.id == this.id;
	}
}