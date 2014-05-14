package act.server.Search;

import java.util.HashSet;
import java.util.Set;

public class ReactionsHypergraphDecompose<N, E> extends ReactionsHypergraph<N, E> {
	private Set<N> reachables;
	
	public void setReachables(Set<N> reachables) {
		this.reachables = new HashSet<N>(reachables);
	}
	
	public ReactionsHypergraphDecompose<N, E> getDecompositions(N target) {
		Set<N> obtained = new HashSet<N>();
		Set<E> applied = new HashSet<E>();
		ReactionsHypergraphDecompose<N, E> result = new ReactionsHypergraphDecompose<N, E>();
		result.setReachables(reachables);
		Set<N> initialSet = getInitialSet();
		obtained.addAll(initialSet);
		Set<N> newNodes = initialSet;
		while (!newNodes.isEmpty()) {
			Set<N> prevNodes = newNodes;
			newNodes = new HashSet<N>();
			for (N n : prevNodes) {
				Set<E> edges = filterReactions(getReactionsFrom(n), obtained);
				for(E e : edges) {
					Set<N> required = getReactants(e);
					if (required != null && required.contains(target)) continue;
					if (applied.contains(e)) continue;
					applied.add(e);
					Set<N> testProducts = new HashSet<N>(getProducts(e));
					Set<N> products = getProducts(e);
					testProducts.removeAll(reachables);
					if (testProducts.size() < 2) {
						result.addReaction(e, getReactants(e), products);
						if (testProducts.size() == 0) {
							for (N p : products) {
								if (!obtained.contains(p)) newNodes.add(p);
								obtained.add(p);
							}
						} else {
							for (N p : testProducts) {
								if (!obtained.contains(p)) newNodes.add(p);
								obtained.add(p);
							}
						}
					}
				}
			}
		}
		if (obtained.contains(target) || target == null) {
			result.setInitialSet(new HashSet<N>(initialSet));
			return result;
		}
		return null;
	}
}
