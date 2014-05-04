
package com.act.reachables;

public class Tunable {
  public static final int INTEGER = 0;
  public static final int BOOLEAN = 1;
  public static final int LIST = 2;

  String key;
  Object val;
  String desc;
  int typ;
  public Tunable(String prop, String desc, int t, Object var_holding_val) {
    this.key = prop;
    this.val = var_holding_val;
    this.desc = desc;
    this.typ = t;
  }

  // Constructor for lists is different
  Object[] opts;
  Object picked_opt;
  public Tunable(String prop, String desc, int t, Object var_holding_val, Object array_of_opts, Object ignoredparam, int default_opt) {
    if (t != Tunable.LIST) {
      System.err.println("Abort. Tunables.LIST constructor called, but not on lists.");
      System.exit(-1);
    }
    this.key = prop;
    this.val = var_holding_val;
    this.desc = desc;
    this.typ = t;

    this.opts = (Object[])array_of_opts;
    this.picked_opt = this.opts[default_opt];
  }

  Boolean valueChanged() {
    // this would be if we had setting written to some file, etc
    // and there was a static method in Tunable that was watching
    // for changes. And if it a key, val pair changed would update 
    // that particular tunable from a static map of all tunables
    return false;
  }

  Object getValue() {
    return this.val;
  }
}
