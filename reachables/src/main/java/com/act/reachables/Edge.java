
package com.act.reachables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import com.act.reachables.Node;
import java.io.Serializable;

public class Edge implements Serializable {
  private static final long serialVersionUID = 6380350196029629374L;
  private static HashMap<Edge, Edge> _edgeCache = new HashMap<>();
  private static HashMap<Edge, HashMap<String, Object>> _attributes = new HashMap<>();

  Node src, dst;
  protected Edge(Node s, Node d) {
    this.src = s;
    this.dst = d;
  }

  public static Edge get(Node src, Node dst, Boolean create) {
    Edge e = new Edge(src, dst);

    Edge got = _edgeCache.get(e);
    if (got != null) {
      return got;
    }

    if (!create)
      return null;

    // the edge cache does not contain edge. create one
    _edgeCache.put(e, e);

    return e;
  }

  public Node getSrc() { return this.src; }
  public Node getDst() { return this.dst; }

  public HashMap<String, Object> getAttr() {
    return Edge._attributes.containsKey(this) ? Edge._attributes.get(this) : null;
  }

  public Object getAttribute(String key) {
    return Edge.getAttribute(this, key);
  }

  public static void setAttribute(Edge e, String key, Object val) {
    if (!Edge._attributes.containsKey(e))
      Edge._attributes.put(e, new HashMap<>());
    Edge._attributes.get(e).put(key, val);
  }

  public static Object getAttribute(Edge e, String key) {
    HashMap<String, Object> kval;
    if (Edge._attributes.containsKey(e) && (kval = Edge._attributes.get(e)).containsKey(key))
      return kval.get(key);
    else
      return null;
  }

  @Override
  public String toString() {
    return new StringBuilder().append(this.src).append("->").append(this.dst).toString();
  }

  @Override
  public int hashCode() {
    return src.hashCode() ^ dst.hashCode();
  }

  @Override
  public boolean equals(Object e) {
    if (!(e instanceof Edge)) return false;
    Edge other = (Edge) e;
    return this.src.equals(other.src) && this.dst.equals(other.dst);
  }
}
