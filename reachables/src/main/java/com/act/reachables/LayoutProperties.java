
package com.act.reachables;

import java.util.HashMap;

public class LayoutProperties {
  HashMap<String, Tunable> tunables;
  String paramset_id;

  LayoutProperties(String name) {
    this.paramset_id = name;
    this.tunables = new HashMap<String, Tunable>();
  }

  void add(Tunable t) {
    this.tunables.put(t.key, t);
  }
  
  Tunable get(String tid) {
    return this.tunables.get(tid);
  }

  void initializeProperties() {
  }

  void updateValues() {
  }

  void revertProperties() {
  }
}
