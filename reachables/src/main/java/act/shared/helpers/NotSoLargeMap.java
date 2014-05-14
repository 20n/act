package act.shared.helpers;

import java.util.HashMap;

public class NotSoLargeMap<V> extends HashMap<String, V> {
	private static final long serialVersionUID = 1L;

	public void put(InchiMapKey largekey, V xref) {
		super.put(largekey.key(), xref);
	}
	
	public V get(InchiMapKey largekey) {
		return super.get(largekey.key());
	}
	
	public boolean containsKey(InchiMapKey largekey) {
		return super.containsKey(largekey.key());
	}

}
