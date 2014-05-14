package act.shared;

import java.util.List;

import act.server.Molecules.RO;

public class ROApplication {
	public Integer roid; 
	public List<String> products;
	public double probability;
	public ReactionType type;
	public RO ro;
	ROApplication(int roid, double prob, List<String> prod, ReactionType type) {
		this.roid = roid;
		this.probability = prob;
		this.products = prod;
		this.type = type;
	}
	
	ROApplication(int roid, RO ro, double prob, List<String> prod, ReactionType type) {
		this.ro = ro;
		this.probability = prob;
		this.products = prod;
		this.type = type;
	}
	
	public String toString() {
		return "id: " + roid + 
				" probability: " + probability + 
				", products: " + products.toString();
	}
}