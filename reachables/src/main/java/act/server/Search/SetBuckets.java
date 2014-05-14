package act.server.Search;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SetBuckets <K, V>{
	private Map<K, Set<V>> myMap;
	
	public SetBuckets() {
		myMap = new HashMap<K, Set<V>>();
	}
	
	/**
	 * Puts value into bucket with key.
	 * Creates new bucket if doesn't exist yet.
	 * @param key
	 * @param value
	 */
	public void put(K key, V value) {
		if (!myMap.containsKey(key)) {
			myMap.put(key, new HashSet<V>());
		}
		myMap.get(key).add(value);
	}
	
	public void putAll(K key, Collection<V> values) {
		if (!myMap.containsKey(key)) {
			myMap.put(key, new HashSet<V>());
		}
		myMap.get(key).addAll(values);
	}
	
	public void putAll(Collection<K> keys, V value) {
		for (K key : keys) {
			if (!myMap.containsKey(key)) {
				myMap.put(key, new HashSet<V>());
			}
			myMap.get(key).add(value);
		}
	}
	
	public void clear(K key) {
		myMap.remove(key);
	}
	
	/**
	 * @param key
	 * @return set of values in bucket.
	 */
	public Set<V> get(K key) {
		return myMap.get(key);
	}
	
	public Set<V> getAll(Collection<K> keys) {
		Set<V> retval = new HashSet<V>();
		for (K key : keys) {
			Set<V> temp = myMap.get(key);
			if (temp != null)
				retval.addAll(temp);
		}
		return retval;
	}
	
	public Set<K> keySet() {
		return myMap.keySet();
	}
	
	public boolean containsKey(K key) {
		return myMap.containsKey(key);
	}
	
	public void remove(K key, V value) {
		myMap.get(key).remove(value);
		if (myMap.get(key).size() == 0)
			myMap.remove(key);
	}
}
