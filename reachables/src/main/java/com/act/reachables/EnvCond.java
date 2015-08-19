package com.act.reachables;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnvCond { 
	private Set<Long> c_ids;
	public EnvCond(List<Long> s) {
		this.c_ids = new HashSet<Long>();
		this.c_ids.addAll(s);
	}
	public Set<Long> speculatedChems() { return this.c_ids; }
	@Override
	public int hashCode() {
		long h = 42;
		for (Long l : this.c_ids) h = h ^ l;
		return (int)h;
	}
	@Override 
	public boolean equals(Object o) {
		if (!(o instanceof EnvCond)) return false;
		EnvCond s = (EnvCond)o;
		return this.c_ids.containsAll(s.c_ids) && s.c_ids.containsAll(this.c_ids);
	}
	@Override
	public String toString() {
		return this.c_ids.toString();
	}
	public String toReadableString(int sz) {
    return toString();
	}
}
