package act.server.Search;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;

public class EnergyRanker extends AbstractHypergraphRanker <EnergyRankingNode, Set> {
	
	public EnergyRanker(MongoDB db, ReactionsHypergraph g) {
		this.db = db;
		this.graph = g;
	}
	
	@Override
	protected EnergyRankingNode createNode(Long id, Integer numParents) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Set<Integer> getCost(Long reactionID) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Set<Integer> join(List<Set<Integer>> costs) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void outputGraph(Long target, String outputFile, int thresh,
			int limit) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int rankPath(ReactionsHyperpath path) {
		List<Long> reactionIDs = path.getOrdering();
		double total = 0;
		for (Long reactionID : reactionIDs) {
			Reaction reaction = db.getReactionFromUUID(reactionID);
			Double cost = reaction.getEstimatedEnergy();
			if (cost == null) {
				total += 10;
			} else {
				total += Math.abs(cost);
			}
		}
		return (int) total;
	}

}

class EnergyRankingNode extends RankingNode {

	@Override
	public Set<Integer> getCosts() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setCosts(Set<Integer> costs) {
		// TODO Auto-generated method stub
		
	}
}
