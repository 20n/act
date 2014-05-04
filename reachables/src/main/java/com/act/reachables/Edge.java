
package com.act.reachables;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import com.act.reachables.Node;

public class Edge {
  private static HashMap<String[], List<Edge>> _edgeCache = new HashMap<String[], List<Edge>>();
  private static HashMap<String, HashMap<String, Object>> _attributes = new HashMap<String, HashMap<String, Object>>();

  Node src, dst;
  String prop, val;
  private String id;
  protected Edge(Node s, Node d, String p, String v) {
    this.src = s;
    this.dst = d;
    this.prop = p;
    this.val = v;
    
    this.id = s.id + "->" + d.id; // computed identifier; not to be computed by users
  }

  public static Edge get(Node src, Node dst, String prop, String val, Boolean create) {
    String[] designator = new String[] { src.id, dst.id, prop, val };
    if (_edgeCache.containsKey(designator)) 
      return (Edge)_edgeCache.get(designator).toArray()[0];

    if (!create)
      return null;

    // the edge cache does not contain edge. create one
    Edge e = new Edge(src, dst, prop, val);
    List<Edge> eset = new ArrayList<Edge>();
    eset.add(e);
    _edgeCache.put(designator, eset);

    return e;
  }

  public String getIdentifier() {
    return this.id;
  }

  public HashMap<String, Object> getAttr() {
    return Edge._attributes.containsKey(this.id) ? Edge._attributes.get(this.id) : null;
  }

  public Object getAttribute(String key) {
    return Edge.getAttribute(this.id, key);
  }

  public static void setAttribute(String id, String key, Object val) {
    if (!Edge._attributes.containsKey(id))
      Edge._attributes.put(id, new HashMap<String, Object>());
    Edge._attributes.get(id).put(key, val);
  }

  public static Object getAttribute(String id, String key) {
    HashMap<String, Object> kval;
    if (Edge._attributes.containsKey(id) && (kval = Edge._attributes.get(id)).containsKey(key))
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
  public boolean equals(Object e) {
    if (!(e instanceof Edge)) return false;
    return this.id.equals(((Edge)e).id);
  }
}
