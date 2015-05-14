
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
  HashMap<Node, Long> nids; // it goes from Node -> Id coz sometimes same ids might be prefixed with r_ or c_ to distinguish categories of nodes
  HashMap<String, Edge> toParentEdge; // indexed by nodeid
  HashMap<String, String> parents; // indexed by nodeid
  HashMap<Long, Integer> tree_depth;

  Network(String name) {
    this.name = name;
    this.nodes = new HashSet<Node>();
    this.edges = new HashSet<Edge>();
    this.nids = new HashMap<Node, Long>();
    this.tree_depth = new HashMap<Long, Integer>();

    this.selectedNodes = new HashSet<Node>();
    this.graph = null;

    this.tree = null;
    this.parents = new HashMap<String, String>();
    this.toParentEdge = new HashMap<String, Edge>();
  }

  // initialized on demand, on first call to jsonstr
  JSONArray graph; 
  JSONObject tree;

  public JSONArray disjointGraphs() throws JSONException {
    if (this.graph == null)
      this.graph = JSONDisjointGraphs.get(this.nodes, this.edges);
    return this.graph;
  }

  public JSONObject disjointTrees() throws JSONException {
    if (this.tree == null)
      this.tree = JSONDisjointTrees.get(this.nodes, this.edges, 
                                    this.parents, this.toParentEdge);
    return this.tree; 
  }

  private void resetJSON() {
    // invalidate any old json representation because of a network update
    // will be recomputed on-demand on next call to jsonstring.
    this.graph = null;
    this.tree = null; 
  }

  void addNode(Node n, Long nid) {
    resetJSON();
    this.nodes.add(n);
    this.nids.put(n, nid);
  }

  void addEdge(Edge e) {
    resetJSON();
    this.edges.add(e);
  }

  void addNodeTreeSpecific(Node n, Long nid, Integer atDepth, String parentid) {
    resetJSON();
    this.nodes.add(n);
    this.nids.put(n, nid);
    this.parents.put(n.id, parentid);
    this.tree_depth.put(nid, atDepth);
  }

  void addEdgeTreeSpecific(Edge e, String childnodeid) {
    resetJSON();
    this.edges.add(e);
    this.toParentEdge.put(childnodeid, e);
  }

  Long get_parent(Long n) {
    // all addNode's are called with Node's whose id is (id: Long).toString()
    String parentid = this.parents.get(n + "");

    return parentid == null ? null : Long.parseLong(parentid);
  }

  HashSet<Node> selectedNodes;
  void unselectAllNodes() {
    this.selectedNodes.clear();
  }

  void setSelectedNodeState(Set<Node> nodes, boolean flag) {
    this.selectedNodes.addAll(nodes);
  }
  
}

class JSONDisjointTrees {
  public static JSONObject get(Set<Node> nodes, Set<Edge> edges, HashMap<String, String> parentIds, HashMap<String, Edge> toParentEdges) throws JSONException {
    // init the json object with structure:
    // {
    //   "name": "nodeid"
    //   "children": [
    //     { "name": "childnodeid", toparentedge: {}, nodedata:.. }, ...
    //   ]
    // }

    HashMap<String, Node> nodeById = new HashMap<String, Node>();
    for (Node n : nodes)
      nodeById.put(n.id, n);

    HashMap<String, JSONObject> nodeObjs = new HashMap<String, JSONObject>();
    // un-deconstruct tree...
    for (String nid : parentIds.keySet()) {
      JSONObject nObj = JSONHelper.nodeObj(nodeById.get(nid));
      nObj.put("name", nid);
      
      if (toParentEdges.get(nid) != null) {
        JSONObject eObj = JSONHelper.edgeObj(toParentEdges.get(nid), null /* no ordering reqd for referencing nodes */);
        nObj.put("edge_up", eObj);
      } else {
        // System.out.println("[INFO] Tree nodes: No parent edge, must be root: " + nid);
      }
      nodeObjs.put(nid, nObj);
    }

    // now that we know that each node has an associated obj
    // link the objects together into the tree structure
    // put each object inside its parent
    HashSet<String> unAssignedToParent = new HashSet<String>(parentIds.keySet());
    for (String nid : parentIds.keySet()) {
      JSONObject child = nodeObjs.get(nid);
      // append child to "children" key within parent
      JSONObject parent = nodeObjs.get(parentIds.get(nid));
      if (parent != null) {
        parent.append("children", child);
        unAssignedToParent.remove(nid);
      } else {
        // System.out.println("[INFO] Tree structure: No parent edge, must be root: " + nid);
      }
    }
    System.out.format("[INFO] In tree %d nodes are without parents: %s\n", unAssignedToParent.size(), unAssignedToParent);

    // outputting a single tree makes front end processing easier
    // we can always remove the root in the front end and get the forest again

    // if many trees remain, assuming they indicate a disjoint forest,
    //    add then as child to a proxy root. 
    // if only one tree then return it

    JSONObject json;
    if (unAssignedToParent.size() == 0) {
      json = null;
      System.err.println("All nodes have parents! Where is the root? Abort."); System.exit(-1);
    } else if (unAssignedToParent.size() == 1) {
      json = unAssignedToParent.toArray(new JSONObject[0])[0]; // return the only element in the set
    } else {
      json = new JSONObject();
      for (String cid : unAssignedToParent) {
        json.put("name" , "root");
        json.append("children", nodeObjs.get(cid));
      }
    }

    return json;
  }

}

class JSONHelper {

  public static JSONObject nodeObj(Node n) throws JSONException {
    JSONObject no = new JSONObject();
    no.put("id", n.id); 
    HashMap<String, Object> attr = n.getAttr();
    for (String k : attr.keySet()) {
      // only output the fields relevants to the reachables tree structure
      if (k.equals("NameOfLen20") || 
          k.equals("ReadableName") ||
          k.equals("Synonyms") ||
          k.equals("InChI") ||
          k.equals("InChiKEY") ||
          k.equals("parent") ||
          k.equals("under_root") ||
          k.equals("num_children") ||
          k.equals("subtreeVendorsSz") ||
          k.equals("subtreeSz") ||
          k.equals("SMILES"))
          no.put(k, attr.get(k).toString());

      if (k.equals("has"))
          no.put(k, attr.get(k));
    }
    // Object v;
    // String label = "" + ((v = n.getAttribute("canonical")) != null ? v : n.id );
    // no.put("name", label ); // required
    // String layer = "" + ((v = n.getAttribute("globalLayer")) != null ? v : 1);
    // no.put("group", layer ); // required: node color by group
    return no;
  }

  public static JSONObject edgeObj(Edge e, HashMap<Node, Integer> order) throws JSONException {
    JSONObject eo = new JSONObject();
    if (order != null) {
      // 1. when printing a graph (and not a tree), the source and target nodes are identified
      // by the array index they appear in the nodes JSONArray. Those indices are contained in the order-map.
      // 2. such an ordering is not required when we are working with trees, so these fields not output there.
      eo.put("source", order.get(e.src)); // required, and have to lookup its order in the node spec
      eo.put("target", order.get(e.dst)); // required, and have to lookup its order in the node spec
    }
    // eo.put("source_id", e.src.id); // only informational
    // eo.put("target_id", e.dst.id); // only informational
    // eo.put("value", 1); // weight of edge: not really needed
    HashMap<String, Object> attr = e.getAttr();
    for (String k : attr.keySet()) {
      // only output the fields relevant to the reachables tree structures
      if (k.equals("under_root") || 
          k.equals("functionalCategory") ||
          k.equals("importantAncestor"))
      eo.put(k, attr.get(k).toString());
    }
    return eo;
  }
}

class JSONDisjointGraphs {

  public static JSONArray get(Set<Node> nodes, Set<Edge> edges) throws JSONException {
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
    JSONArray json = new JSONArray();

    HashMap<Long, Set<Node>> treenodes = new HashMap<Long, Set<Node>>();
    HashMap<Long, Set<Edge>> treeedges = new HashMap<Long, Set<Edge>>();
    for (Node n : nodes) {
      Long k = (Long)n.getAttribute("under_root");
      if (!treenodes.containsKey(k)) {
        treenodes.put(k, new HashSet<Node>());
        treeedges.put(k, new HashSet<Edge>());
      }
      treenodes.get(k).add(n);
    }

    for (Edge e : edges) {
      Long k = (Long)e.getAttribute("under_root");
      if (!treeedges.containsKey(k)) {
        System.err.println("Fatal: Edge found rooted under a tree (under_root) that has no node!");
        System.exit(-1);
      }
      treeedges.get(k).add(e);
    }

    for (Long root : treenodes.keySet()) {
      JSONObject tree = new JSONObject();
      HashMap<Node, Integer> nodeOrder = new HashMap<Node, Integer>();
      tree.put("nodes", nodeListObj(treenodes.get(root), nodeOrder /*inits this ordering*/));
      tree.put("links", edgeListObj(treeedges.get(root), nodeOrder /* uses the ordering */));
      
      json.put(tree);
    }

    return json;

  }

  private static JSONArray nodeListObj(Set<Node> treenodes, HashMap<Node, Integer> nodeOrder) throws JSONException {
    JSONArray a = new JSONArray();
    Node[] nodesAr = treenodes.toArray(new Node[0]);
    for (int i = 0; i < nodesAr.length; i++) {
      Node n = nodesAr[i];
      a.put(i, JSONHelper.nodeObj(n)); // put the object at index i in the array
      nodeOrder.put(n, i);
    }
    return a;
  }
  
  private static JSONArray edgeListObj(Set<Edge> treeedges, HashMap<Node, Integer> order) throws JSONException {
    JSONArray a = new JSONArray();
    for (Edge e : treeedges)
      a.put(JSONHelper.edgeObj(e, order));
    return a;
  }


}
