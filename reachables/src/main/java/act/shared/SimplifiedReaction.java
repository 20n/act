package act.shared;

public class SimplifiedReaction {
	private long uuid, substrate, product;
	private ReactionType type;
	private String fullReaction;
	private Double weight;
	
	public SimplifiedReaction(long uuid, Long substrate, Long product, ReactionType type) {
		this.uuid = uuid;
		this.substrate = substrate;
		this.product = product;
		this.type = type;
	}
	
	public long getUuid() {
		return uuid;
	}

	public void setUuid(long uuid) {
		this.uuid = uuid;
	}

	public long getSubstrate() {
		return substrate;
	}

	public void setSubstrate(long substrate) {
		this.substrate = substrate;
	}

	public long getProduct() {
		return product;
	}

	public void setProduct(long product) {
		this.product = product;
	}

	public ReactionType getType() {
		return type;
	}

	public void setType(ReactionType type) {
		this.type = type;
	}

	public String getFullReaction() {
		return fullReaction;
	}

	public void setFullReaction(String fullReaction) {
		this.fullReaction = fullReaction;
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(Double weight) {
		this.weight = weight;
	}
}
