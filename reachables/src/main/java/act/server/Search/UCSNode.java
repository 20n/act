package act.server.Search;

public abstract class UCSNode {
	private double cost;
	private UCSNode parent;
	
	public UCSNode(UCSNode parent, double cost) {
		this.parent = parent;
		this.cost = cost;
	}
	
	public double getCost() { return cost; }
	public UCSNode getParent() { return parent; }
}
