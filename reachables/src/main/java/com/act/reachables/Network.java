
package com.act.reachables;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Network implements Serializable {
  private static final long serialVersionUID = 4643733150478812924L;
  String name;
  HashSet<Node> nodes;
  HashMap<Node, Node> nodeMapping;
  HashSet<Edge> edges;
  HashMap<Long, Node> idToNode;
  HashMap<Pair<Node, Node>, Edge> edgeHash;
  HashMap<Node, Long> nids; // it goes from Node -> Id coz sometimes same ids might be prefixed with r_ or c_ to distinguish categories of nodeMapping
  HashMap<Long, Edge> toParentEdge; // indexed by nodeid
  HashMap<Long, Long> parents; // indexed by nodeid
  HashMap<Long, Integer> tree_depth;
  HashMap<Node, Set<Edge>> edgesGoingToNode;
  HashMap<Long, Set<Edge>> edgesGoingToId;

  Network(String name) {
    this.name = name;
    this.nodes =  new HashSet<Node>();
    this.nodeMapping = new HashMap<Node, Node>();
    this.edges = new HashSet<Edge>();
    this.nids = new HashMap<Node, Long>();
    this.tree_depth = new HashMap<Long, Integer>();
    this.edgesGoingToNode = new HashMap<>();
    this.edgesGoingToId = new HashMap<>();
    this.edgeHash = new HashMap<>();
    this.idToNode = new HashMap<>();

    this.selectedNodes = new HashSet<Node>();
    this.parents = new HashMap<>();
    this.toParentEdge = new HashMap<>();
  }

  public Map<Node, Long> nodesAndIds() {
    return this.nids;
  }

  public JSONArray disjointGraphs(MongoDB db) throws JSONException {
    return JSONDisjointGraphs.get(db, new HashSet(this.nodeMapping.values()), this.edges);
  }

  public JSONObject disjointTrees(MongoDB db) throws JSONException {
    return JSONDisjointTrees.get(db, new HashSet(this.nodeMapping.values()), this.edges,
                                    this.parents, this.toParentEdge);
  }

  void addNode(Node n, Long nid) {
    if (this.nodeMapping.containsKey(n)) {
      if (!Boolean.valueOf((String)Node.getAttribute(n.id, "isrxn"))) {
        return;
      }

      Node currentNode = this.nodeMapping.get(n);
      if (Node.getAttribute(nid, "reaction_ids") != null) {
        HashSet s = ((HashSet) Node.getAttribute(currentNode.id, "reaction_ids"));
        s.addAll((HashSet) Node.getAttribute(nid, "reaction_ids"));

        Node.setAttribute(currentNode.id, "reaction_ids", s);
        Node.setAttribute(currentNode.id, "reaction_count", s.size());
      }

      if (Node.getAttribute(nid, "organisms") != null) {
        HashSet orgs = ((HashSet) Node.getAttribute(currentNode.id, "organisms"));
        orgs.addAll((HashSet) Node.getAttribute(nid, "organisms"));

        Node.setAttribute(currentNode.id, "organisms", orgs);
      }
     } else {
      this.idToNode.put(nid, n);
      this.nodeMapping.put(n, n);
      this.nids.put(n, nid);
    }
  }

  public Node getNodeById(Long id){
    return this.idToNode.get(id);
  }

  void addEdge(Edge e) {
    this.edges.add(e);

    if (this.edgesGoingToNode.containsKey(e.getDst())) {
      this.edgesGoingToNode.get(e.getDst()).add(e);
      this.edgesGoingToId.get(e.getDst().id).add(e);

    } else {
      Set<Edge> newEdgeList = new HashSet<>();
      newEdgeList.add(e);
      this.edgesGoingToNode.put(e.getDst(), newEdgeList);
      this.edgesGoingToId.put(e.getDst().id, newEdgeList);
    }
  }

  public Edge getEdge(Node src, Node dst) {
    return this.edgeHash.get(Pair.of(src, dst));
  }

  public Set<Edge> getEdgesGoingInto(Node n) {
    return this.edgesGoingToNode.get(n);
  }

  public Set<Edge> getEdgesGoingInto(Long id) {
    return this.edgesGoingToId.get(id);
  }

  void mergeInto(Network that) {
    // this is only written to work for graphs, not
    // specifically trees, expect undefined behaviour
    // if you are keeping track of trees
    that.nodeMapping.values().forEach(n -> addNode(n, n.id));
    that.edges.stream().forEach(this::addEdge);

    this.nids.putAll(that.nids);

  }

  public void serialize(String toFile) {
    try {
      OutputStream file = new FileOutputStream(toFile);
      OutputStream buffer = new BufferedOutputStream(file);
      ObjectOutput output = new ObjectOutputStream(buffer);
      try {
        output.writeObject(this);
      } finally {
        output.close();
      }
    } catch(IOException ex) {
      throw new RuntimeException("Network serialize failed: " + ex);
    }
  }

  public static Network deserialize(String fromFile) {
    try {
      InputStream file = new FileInputStream(fromFile);
      InputStream buffer = new BufferedInputStream(file);
      ObjectInput input = new ObjectInputStream(buffer);
      try {
        return (Network) input.readObject();
      } finally {
        input.close();
      }
    } catch(ClassNotFoundException ex) {
      throw new RuntimeException("Network deserialize failed: Class not found: " + ex);
    } catch(IOException ex) {
      throw new RuntimeException("Network deserialize failed: IO problem: " + ex);
    }
  }
  public String toDOT() {
    List<String> lines = new ArrayList<String>();

    lines.add("digraph " + this.name + " {");

    for (Node n : new ArrayList<Node>(this.nodeMapping.values())) {
      String id;
      String label;
      String tooltip;
      String url;
      String color = Cascade.quote("black");

      if (Boolean.valueOf((String)Node.getAttribute(n.id, "isrxn"))) {
        id = String.valueOf(n.getIdentifier());

        int reactionCount = (int) Node.getAttribute(n.id, "reaction_count");

        Set<String> rawLabel = (HashSet) Node.getAttribute(n.id, "label_string");
        List<String> filteredRawLabel = rawLabel.stream().filter(x -> !x.equals("")).collect(Collectors.toList());

        Long labelId = n.getIdentifier() - Cascade.rxnIdShift();
        if (labelId < 0){
          labelId = Reaction.reverseNegativeId(labelId);
        }

        HashSet<String> organisms = (HashSet<String>) Node.getAttribute(n.id, "organisms");

        String fullLabel;
        if (filteredRawLabel.isEmpty()) {
          if ((boolean) Node.getAttribute(n.id, "isSpontaneous")){
            fullLabel = "Spontaneous";
          } else {
            fullLabel = "Not Available";
          }
        } else {
          fullLabel = filteredRawLabel.get(0);
          if (filteredRawLabel.size() > 1) {
            fullLabel += " and " + String.valueOf(filteredRawLabel.size() - 1) + " more";
          }
        }

        label = Cascade.quote(fullLabel);
        tooltip = Cascade.quote((String)Node.getAttribute(n.id, "tooltip_string"));
        if ((boolean) Node.getAttribute(n.id, "hasSequence")) {
          String forestGreen = "#228B22";
          color = Cascade.quote(forestGreen);
        } else if ((boolean) Node.getAttribute(n.id, "isSpontaneous")) {
          String goldenrodYellow = "#E8BD2B";
          color = Cascade.quote(goldenrodYellow);
        } else {
          String crimsonRed = "#DC143C";
          color = Cascade.quote(crimsonRed);
        }

        url = Cascade.quote((String)Node.getAttribute(n.id, "url_string"));
      } else {
        id = String.valueOf(n.getIdentifier());

        label = (String)Node.getAttribute(n.id, "label_string");
        if (label == null) {
          label = n.id >= Cascade.rxnIdShift() ? "Reaction_" + n.id.toString() : "Chemical_" + n.id.toString();
        }
        tooltip = (String)Node.getAttribute(n.id, "tooltip_string");
        url = (String)Node.getAttribute(n.id, "url_string");
      }


      String node_line = id
        + " [shape=box,"
        + " label=" + label + ","
        + " tooltip=" + tooltip + ","
        + " URL=" + url + ","
        + " color=" + color + ","
        + "];";
      lines.add(node_line);
    }


    for (Edge e : new ArrayList<Edge>(this.edges)) {
      // create a line for nodeMapping like so:
      // id -> id;
      Long src_id = e.getSrc().getIdentifier();
      Long dst_id = e.getDst().getIdentifier();

      String edge_line;
      if (e.getAttribute("color") != null) {
        edge_line = src_id + " -> " + dst_id + " [color=" + e.getAttribute("color") + "]" + ";";
      } else {
        edge_line = src_id + " -> " + dst_id + ";";
      }

      lines.add(edge_line);

      Edge.setAttribute(e, "color", null);
    }

    lines.add("}");

    return StringUtils.join(lines.toArray(new String[0]), "\n");
  }

  void addNodeTreeSpecific(Node n, Long nid, Integer atDepth, Long parentid) {
    this.nodeMapping.put(n, n);
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
        JSONObject eObj = JSONHelper.edgeObj(toParentEdges.get(nid), null /* no ordering reqd for referencing nodeMapping */);
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
      throw new RuntimeException("All nodeMapping have parents! Where is the root? Abort.");
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
      // 1. when printing a graph (and not a tree), the source and target nodeMapping are identified
      // by the array index they appear in the nodeMapping JSONArray. Those indices are contained in the order-map.
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
    //   "nodeMapping":[
    //     { "name":"Myriel", "group":1 }, ...
    //   ],
    //   "links":[
    //     { "source":1, "target":0, "value":1 }, ...
    //   ]
    // }
    // nodeMapping.group specifies the node color
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
      tree.put("nodeMapping", nodeListObj(db, treenodes.get(root), nodeOrder /*inits this ordering*/));
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
