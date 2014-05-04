
package com.act.reachables;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class Node {
  private static HashMap<String, List<Node>> _nodeCache = new HashMap<String, List<Node>>();
  private static HashMap<String, HashMap<String, Object>> _attributes = new HashMap<String, HashMap<String, Object>>();

  String id;
  protected Node(String id) {
    this.id = id;
  }

  public static Node get(String id, Boolean create) {
    if (_nodeCache.containsKey(id)) 
      return (Node)_nodeCache.get(id).get(0);

    if (!create)
      return null;

    // the edge cache does not contain edge. create one
    Node n = new Node(id);
    List<Node> nset = new ArrayList<Node>();
    nset.add(n);
    _nodeCache.put(id, nset);

    return n;
  }

  public String getIdentifier() {
    return this.id;
  }

  public HashMap<String, Object> getAttr() {
    return Node._attributes.containsKey(this.id) ? Node._attributes.get(this.id) : null;
  }

  public Object getAttribute(String key) {
    return Node.getAttribute(this.id, key);
  }

  public static void setAttribute(String id, String key, Object val) {
    if (!Node._attributes.containsKey(id))
      Node._attributes.put(id, new HashMap<String, Object>());
    Node._attributes.get(id).put(key, val);
  }

  public static Object getAttribute(String id, String key) {
    HashMap<String, Object> kval;
    if (Node._attributes.containsKey(id) && (kval = Node._attributes.get(id)).containsKey(key))
      return kval.get(key);
    else
      return null;
  }

  @Override
  public String toString() {
    return this.id;
  }

  @Override
  public int hashCode() {
    return this.id.hashCode();
  }

  @Override
  public boolean equals(Object n) {
    if (!(n instanceof Node)) return false;
    return this.id.equals(((Node)n).id);
  }
}
