
package com.act.reachables;

import act.server.MongoDB;
import act.shared.Chemical;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Network implements Serializable {
  private static final long serialVersionUID = 4643733150478812924L;
  String name;
  HashSet<Node> nodes;
  HashSet<Edge> edges;
  HashMap<Node, Long> nids; // it goes from Node -> Id coz sometimes same ids might be prefixed with r_ or c_ to distinguish categories of nodes
  HashMap<Long, Edge> toParentEdge; // indexed by nodeid
  HashMap<Long, Long> parents; // indexed by nodeid
  HashMap<Long, Integer> tree_depth;

  Network(String name) {
    this.name = name;
    this.nodes = new HashSet<Node>();
    this.edges = new HashSet<Edge>();
    this.nids = new HashMap<Node, Long>();
    this.tree_depth = new HashMap<Long, Integer>();

    this.selectedNodes = new HashSet<Node>();
    this.parents = new HashMap<>();
    this.toParentEdge = new HashMap<>();
  }

  public Map<Node, Long> nodesAndIds() {
    return this.nids;
  }

  public JSONArray disjointGraphs(MongoDB db) throws JSONException {
    return JSONDisjointGraphs.get(db, this.nodes, this.edges);
  }

  public JSONObject disjointTrees(MongoDB db) throws JSONException {
    return JSONDisjointTrees.get(db, this.nodes, this.edges,
                                    this.parents, this.toParentEdge);
  }

  void addNode(Node n, Long nid) {
    this.nodes.add(n);
    this.nids.put(n, nid);
  }

  void addEdge(Edge e) {
    this.edges.add(e);
  }

  void mergeInto(Network that) {
    // this is only written to work for graphs, not
    // specifically trees, expect undefined behaviour
    // if you are keeping track of trees

    this.nodes.addAll(that.nodes);
    this.edges.addAll(that.edges);
    this.nids.putAll(that.nids);
  }

  public String toDOT() {
    List<String> lines = new ArrayList<String>();

    lines.add("digraph " + this.name + " {");

    for (Node n : this.nodes) {
      // create a line for nodes like so:
      // nident [label="displayname"];
      String id;
      String label;
      String tooltip;
      String url;

      if (Boolean.valueOf((String)Node.getAttribute(n.id, "isrxn"))) {
        id = String.valueOf(n.getIdentifier());

        List<String> rawLabel = Arrays.asList(((String)Node.getAttribute(n.id, "label_string")).split("&&&&"));

        label = Cascade.quote(StringUtils.join(new HashSet<>(rawLabel), ", ") + " [#" + rawLabel.size() + " " + String.valueOf(n.getIdentifier() - Cascade.rxnIdShift()) + "]");

        List<String> rawTooltip = Arrays.asList(((String)Node.getAttribute(n.id, "tooltip_string")).split("&&&&"));
        tooltip = Cascade.quote(rawTooltip.get(0));

        url = Cascade.quote((String)Node.getAttribute(n.id, "url_string"));

      } else {
        id = String.valueOf(n.getIdentifier());
        label = (String)Node.getAttribute(n.id, "label_string");
        tooltip = (String)Node.getAttribute(n.id, "tooltip_string");
        url = (String)Node.getAttribute(n.id, "url_string");
      }

      String node_line = id
        + " [shape=box,"
        + " label=" + label + ","
        + " tooltip=" + tooltip + ","
        + " URL=" + url + ","
        + "];";
      lines.add(node_line);
    }

    for (Edge e : this.edges) {
      // create a line for nodes like so:
      // id -> id;
      Long src_id = e.getSrc().getIdentifier();
      Long dst_id = e.getDst().getIdentifier();

      String edge_line = src_id + " -> " + dst_id + ";";
      lines.add(edge_line);
    }

    lines.add("}");

    return StringUtils.join(lines.toArray(new String[0]), "\n");
  }

  void addNodeTreeSpecific(Node n, Long nid, Integer atDepth, Long parentid) {
    this.nodes.add(n);
    this.nids.put(n, nid);
    this.parents.put(n.id, parentid);
    this.tree_depth.put(nid, atDepth);
  }

  public HashMap<Long, Integer> nodeDepths() {
    return this.tree_depth;
  }

  void addEdgeTreeSpecific(Edge e, Long childnodeid) {
    this.edges.add(e);
    this.toParentEdge.put(childnodeid, e);
  }

  Long get_parent(Long n) {
    // all addNode's are called with Node's whose id is (id: Long).toString()
    return this.parents.get(n);
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
  public static JSONObject get(MongoDB db, Set<Node> nodes, Set<Edge> edges,
                               HashMap<Long, Long> parentIds, HashMap<Long, Edge> toParentEdges)
      throws JSONException {
    // init the json object with structure:
    // {
    //   "name": "nodeid"
    //   "children": [
    //     { "name": "childnodeid", toparentedge: {}, nodedata:.. }, ...
    //   ]
    // }

    HashMap<Long, Node> nodeById = new HashMap<>();
    for (Node n : nodes)
      nodeById.put(n.id, n);

    HashMap<Long, JSONObject> nodeObjs = new HashMap<>();
    // un-deconstruct tree...
    for (Long nid : parentIds.keySet()) {
      JSONObject nObj = JSONHelper.nodeObj(db, nodeById.get(nid));
      nObj.put("name", nid);

      if (toParentEdges.get(nid) != null) {
        JSONObject eObj = JSONHelper.edgeObj(toParentEdges.get(nid), null /* no ordering reqd for referencing nodes */);
        nObj.put("edge_up", eObj);
      } else {
      }
      nodeObjs.put(nid, nObj);
    }

    // now that we know that each node has an associated obj
    // link the objects together into the tree structure
    // put each object inside its parent
    HashSet<Long> unAssignedToParent = new HashSet<>(parentIds.keySet());
    for (Long nid : parentIds.keySet()) {
      JSONObject child = nodeObjs.get(nid);
      // append child to "children" key within parent
      JSONObject parent = nodeObjs.get(parentIds.get(nid));
      if (parent != null) {
        parent.append("children", child);
        unAssignedToParent.remove(nid);
      } else {
      }
    }

    // outputting a single tree makes front end processing easier
    // we can always remove the root in the front end and get the forest again

    // if many trees remain, assuming they indicate a disjoint forest,
    //    add then as child to a proxy root.
    // if only one tree then return it

    JSONObject json;
    if (unAssignedToParent.size() == 0) {
      json = null;
      throw new RuntimeException("All nodes have parents! Where is the root? Abort.");
    } else if (unAssignedToParent.size() == 1) {
      json = unAssignedToParent.toArray(new JSONObject[0])[0]; // return the only element in the set
    } else {
      json = new JSONObject();
      for (Long cid : unAssignedToParent) {
        json.put("name" , "root");
        json.append("children", nodeObjs.get(cid));
      }
    }

    return json;
  }
  /* Note: there is a streaming variant of JSON tree serialization in the commit history if it is ever needed.
   * See f45bc81818322b8054cf88ae1c22ba5ad654a5d1 for details. */
}

class JSONHelper {

  public static JSONObject nodeObj(MongoDB db, Node n) throws JSONException {
    Chemical thisChemical = db.getChemicalFromChemicalUUID(n.id);
    JSONObject no = thisChemical == null ? new JSONObject() :
        new JSONObject(ComputeReachablesTree.getExtendedChemicalInformationJSON(thisChemical));
    no.put("id", n.id);
    HashMap<String, Serializable> attr = n.getAttr();
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
    HashMap<String, Serializable> attr = e.getAttr();
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

  public static JSONArray get(MongoDB db, Set<Node> nodes, Set<Edge> edges) throws JSONException {
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
        throw new RuntimeException("Fatal: Edge found rooted under a tree (under_root) that has no node!");
      }
      treeedges.get(k).add(e);
    }

    for (Long root : treenodes.keySet()) {
      JSONObject tree = new JSONObject();
      HashMap<Node, Integer> nodeOrder = new HashMap<Node, Integer>();
      tree.put("nodes", nodeListObj(db, treenodes.get(root), nodeOrder /*inits this ordering*/));
      tree.put("links", edgeListObj(treeedges.get(root), nodeOrder /* uses the ordering */));

      json.put(tree);
    }

    return json;

  }

  private static JSONArray nodeListObj(MongoDB db, Set<Node> treenodes, HashMap<Node, Integer> nodeOrder) throws JSONException {
    JSONArray a = new JSONArray();
    Node[] nodesAr = treenodes.toArray(new Node[0]);
    for (int i = 0; i < nodesAr.length; i++) {
      Node n = nodesAr[i];
      a.put(i, JSONHelper.nodeObj(db, n)); // put the object at index i in the array
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
