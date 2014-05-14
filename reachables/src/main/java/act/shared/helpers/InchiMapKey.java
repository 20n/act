package act.shared.helpers;

public class InchiMapKey extends LargeMapKey {
	private static final long serialVersionUID = 1L;
	String inchi;
	
	public InchiMapKey(String inchi) {
		super(inchi.hashCode());
		this.inchi = inchi;
	}

	@Override
	public boolean equals(Object o) {
        if (!(o instanceof InchiMapKey))
            return false;
        return ((InchiMapKey)o).inchi.equals(this.inchi);
	}

	@Override
	public int hashCode() {
		return this.inchi.hashCode();
	}

	public String key() {
		return this.inchi;
	}
	
}