package act.server.Search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A Counter provides a cleaner interface than a Map
 * for managing counts.
 *
 * @param <K>
 */
public class Counter<K> {
	private Map<K, Integer> myMap;
	
	public Counter() {
		myMap = new HashMap<K, Integer>();
	}
	
	public Integer get(K key) {
		if (myMap.containsKey(key)) return myMap.get(key);
		return 0;
	}
	
	public void put(K key, int value) {
		if (value == 0) {
			if (myMap.containsKey(key)) myMap.remove(key);
			return;
		}
		myMap.put(key, value);
	}
	
	/**
	 * 
	 * @return set of keys with a non-zero count
	 */
	public Set<K> keySet() { return myMap.keySet(); }
	
	public void scale(int scale) {
		Set<K> keys = new HashSet(keySet());
		for (K key : keys) {
			myMap.put(key, get(key) * scale);
		}
	}
	
	public void inc(K key) { inc(key, 1); }
	public void dec(K key) { inc(key, -1); }
	public void dec(K key, int amount) { inc(key, -amount); }
	
	public void inc(K key, int amount) {
		if (!myMap.containsKey(key))
			myMap.put(key, 0);
		
		int newVal = myMap.get(key) + amount;
		if (newVal == 0) {
			myMap.remove(key);
		} else {
			myMap.put(key, newVal);
		}
		
	}

	public void subBy(Counter<K> other) {
		for (K key : other.keySet()) {
			dec(key, other.get(key));
		}
	}
	
	public void addBy(Counter<K> other) {
		for (K key : other.keySet()) {
			inc(key, other.get(key));
		}
	}
	
	public Counter<K> add(Counter<K> other) {
		Counter<K> result = new Counter<K>();
		result.addBy(this);
		result.addBy(other);
		return result;
	}
	
	public Counter<K> sub(Counter<K> other) {
		Counter<K> result = new Counter<K>();
		result.addBy(this);
		result.subBy(other);
		return result;
	}
	
	public Counter<K> max(Counter<K> other) {
		Counter<K> result = new Counter<K>();
		for (K key : other.keySet()) {
			if (other.get(key) > this.get(key)) {
				result.put(key, other.get(key));
			} else {
				result.put(key, this.get(key));
			}
		}
		return result;
	}
	
	public Map<K, Integer> getMap() { return new HashMap<K, Integer>(myMap); }
	
	public int getTotal() {
		int total = 0;
		for (K key : myMap.keySet()) {
			total += get(key);
		}
		return total;
	}
	
	public int getAbsTotal() {
		int total = 0;
		for (K key : myMap.keySet()) {
			total += Math.abs(myMap.get(key));
		}
		return total;
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Counter)) return false;
		@SuppressWarnings("rawtypes")
		Counter otherCounter = (Counter) other;
		return otherCounter.myMap.equals(myMap);
	}
	
	@Override
	public int hashCode() {
		return myMap.hashCode();
	}
	
	@Override
	public String toString() {
		String result = "";
		for (K key : myMap.keySet()) {
			result += key + ": " + myMap.get(key) + ", ";
		}
		return result;
	}
	
	public Counter<K> clone() {
		Counter<K> counter = new Counter<K>();
		for (K key : keySet()) {
			counter.put(key, get(key));
		}
		return counter;
	}
}
