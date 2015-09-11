
package com.act.reachables;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;

public class Node implements Serializable {
  private static final long serialVersionUID = -6907101658540501637L;

  Long id;
  protected Node(Long id) {
    this.id = id;
  }

  public static Node get(Long id, Boolean create) {
    if (ActData.instance().nodeCache.containsKey(id))
      return (Node)ActData.instance().nodeCache.get(id).get(0);

    if (!create)
      return null;

    // the edge cache does not contain edge. create one
    Node n = new Node(id);
    List<Node> nset = new ArrayList<Node>();
    nset.add(n);
    ActData.instance().nodeCache.put(id, nset);

    return n;
  }

  public Long getIdentifier() {
    return this.id;
  }

  public HashMap<String, Serializable> getAttr() {
    return ActData.instance().nodeAttributes.get(this.id);
  }

  public Object getAttribute(String key) {
    return Node.getAttribute(this.id, key);
  }

  public static void setAttribute(Long id, String key, Serializable val) {
    if (!ActData.instance().nodeAttributes.containsKey(id))
      ActData.instance().nodeAttributes.put(id, new HashMap<String, Serializable>());
    ActData.instance().nodeAttributes.get(id).put(key, val);
  }

  public static Object getAttribute(Long id, String key) {
    HashMap<String, Serializable> kval;
    if (ActData.instance().nodeAttributes.containsKey(id) &&
        (kval = ActData.instance().nodeAttributes.get(id)).containsKey(key))
      return kval.get(key);
    else
      return null;
  }

  @Override
  public String toString() {
    return this.id.toString();
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
