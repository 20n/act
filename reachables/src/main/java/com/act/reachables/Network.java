
package com.act.reachables;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class Network {
  String name;
  HashSet<Node> nodes;
  HashSet<Edge> edges;

  Network(String name) {
    this.name = name;
    this.nodes = new HashSet<Node>();
    this.edges = new HashSet<Edge>();

    this.selectedNodes = new HashSet<Node>();
    this.json = null;
  }

  // initialized on demand, on first call to jsonstr
  JSONObject json; 

  public String jsonstr() throws JSONException {
    if (json == null)
      initJSON();
    return json.toString(2); // indent = 2 spaces
  }

  private void initJSON() throws JSONException {
    // init the json object with structure:
    // {
    //   "nodes":[
    //     { "name":"Myriel", "group":1 }, ...
    //   ],
    //   "links":[
    //     { "source":1, "target":0, "value":1 }, ...
    //   ]
    // }
    // nodes.group specifies the node color
    // links.value specifies the edge weight
    HashMap<Node, Integer> nodeOrder = new HashMap<Node, Integer>();
    this.json = new JSONObject();
    this.json.put("nodes", nodeListObj(nodeOrder /*inits this ordering*/));
    this.json.put("links", edgeListObj(nodeOrder /* uses the ordering */));
  }

  private JSONArray nodeListObj(HashMap<Node, Integer> nodeOrder) throws JSONException {
    JSONArray a = new JSONArray();
    Node[] nodesAr = this.nodes.toArray(new Node[0]);
    for (int i = 0; i < nodesAr.length; i++) {
      Node n = nodesAr[i];
      a.put(i, nodeObj(n, i)); // put the object at index i in the array
      nodeOrder.put(n, i);
    }
    return a;
  }
  
  private JSONObject nodeObj(Node n, int idx) throws JSONException {
    JSONObject no = new JSONObject();
    no.put("id", n.id); 
    no.put("idx", idx); 
    HashMap<String, Object> attr = n.getAttr();
    for (String k : attr.keySet()) {
      no.put(k, attr.get(k).toString());
    }
    Object v;
    String label = "" + ((v = n.getAttribute("canonical")) != null ? v : n.id );
    no.put("name", label ); // required
    String layer = "" + ((v = n.getAttribute("globalLayer")) != null ? v : 1);
    no.put("group", layer ); // required: node color by group
    return no;
  }

  private JSONArray edgeListObj(HashMap<Node, Integer> order) throws JSONException {
    JSONArray a = new JSONArray();
    for (Edge e : this.edges)
      a.put(edgeObj(e, order));
    return a;
  }

  private JSONObject edgeObj(Edge e, HashMap<Node, Integer> order) throws JSONException {
    JSONObject eo = new JSONObject();
    eo.put("source", order.get(e.src)); // required, and have to lookup its order in the node spec
    eo.put("target", order.get(e.dst)); // required, and have to lookup its order in the node spec
    eo.put("source_id", e.src.id); 
    eo.put("target_id", e.dst.id); 
    eo.put("value", 1); // required: weight of edge
    HashMap<String, Object> attr = e.getAttr();
    for (String k : attr.keySet()) {
      eo.put(k, attr.get(k).toString());
    }
    return eo;
  }

  private void resetJSON() {
    // invalidate any old json representation because of a network update
    // will be recomputed on-demand on next call to jsonstring.
    json = null; 
  }

  void addNode(Node n) {
    resetJSON();
    this.nodes.add(n);
  }

  void addEdge(Edge e) {
    resetJSON();
    this.edges.add(e);
  }

  HashSet<Node> selectedNodes;
  void unselectAllNodes() {
    this.selectedNodes.clear();
  }

  void setSelectedNodeState(Set<Node> nodes, boolean flag) {
    this.selectedNodes.addAll(nodes);
  }
  
}

