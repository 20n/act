package act.graph;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.server.EnumPath.OperatorSet.OpID;
import act.shared.helpers.*;

public class Graph<N,E>  implements java.io.Serializable {
  private static final long serialVersionUID = 4914489242431995004L;
  HashMap<String, Object> metadata;
  public void setMetaData(String k, Object m) { this.metadata.put(k, m); }
  public Object getMetaData(String k) { return this.metadata.get(k); }

  // private List<Node<N>> nodesOrig; private List<Edge<N,E>> edgesOrig; // only for pretty printing
  private int maxNodeID; // accessed by FlatPossibleGraph
  private HashMap<Integer, N> nTypes;
  private HashMap<Integer, List<P<Integer, E>>> adjList;

  public HashMap<Integer, List<P<Integer, E>>> AdjList() { return this.adjList; }
  public HashMap<Integer, N> Nodes() { return this.nTypes; }
  public int MaxNodeIDContained() { return this.maxNodeID; }

  public int size() {
    return this.nTypes.size();
  }

  public void writeDOT(String fname) throws IOException {
    BufferedWriter dotf = new BufferedWriter(new FileWriter(fname, false)); // open for overwrite
    // taking hints from http://en.wikipedia.org/wiki/DOT_language#A_simple_example
    String graphname = "enumerated_graph";
    dotf.write("graph " + graphname + " {\n");
    dotf.write("\tnode [shape=plaintext]");
    for (Integer nid : this.adjList.keySet()) {
      for (P<Integer, E> e : this.adjList.get(nid))
        dotf.write("\t" + dot_edge(nid, e.fst(), e.snd()) + ";\n");
    }
    dotf.write("}");
    dotf.close();
  }

  private String dot_edge(Integer src, Integer dst, E snd) {
    // create and edge of the form: B2 -> B3 [label="edgelabel"]
    return src + " -> " + dst + " [label=\"" + snd + "\"]";
  }

  public void AddNode(Node<N> node)
  {
    if (node.id < 0 || this.nTypes.containsKey(node.id)) {
      System.err.println("Either id is negative or we already have it installed." + node.id);
      System.exit(-1);
    }
    if (node.id > this.maxNodeID) this.maxNodeID = node.id;
    this.nTypes.put(node.id, node.atom);
    if (!this.adjList.containsKey(node.id))
      this.adjList.put(node.id, new ArrayList<P<Integer, E>>());

    // for pretty printing...
    // this.nodesOrig.add(node);
  }

  public void AddEdge(Node<N> src, Node<N> dst, E et)
  {
    if (!adjList.containsKey(src.id))
      adjList.put(src.id, new ArrayList<P<Integer, E>>());
    if (!adjList.containsKey(dst.id))
      adjList.put(dst.id, new ArrayList<P<Integer, E>>());
    // no duplicates...
    P<Integer, E> dstE;
    dstE = new P<Integer, E>(dst.id, et);
    if (!adjList.get(src.id).contains(dstE))
      adjList.get(src.id).add(dstE);
    dstE = new P<Integer, E>(src.id, et);
    if (!adjList.get(dst.id).contains(dstE))
      adjList.get(dst.id).add(dstE);

    // for pretty printing....
    // this.edgesOrig.add(new Edge<N,E>(src, dst, et));
  }

  public void CreateNewNodeAndAddEdgeTo(Integer id, Integer nid, N newdata, E e) {
    Node<N> n = new Node<N>(nid, newdata);
    AddNode(n);
    Node<N> src = new Node<N>(id, this.nTypes.get(id));
    // src: this this is not the original node, the original is not kept
    // around anywhere and the Node<N> is just a wrapper anyway
    AddEdge(src, n, e);
  }

  public Set<T<Integer, Integer, E>> computeEdgeSetFromAdjList() {
    Set<T<Integer, Integer, E>> edges = new HashSet<T<Integer, Integer, E>>();
    for (Integer src : this.adjList.keySet()) {
      for (P<Integer, E> edge : this.adjList.get(src)) {
        edges.add(new T<Integer, Integer, E>(src, edge.fst(), edge.snd()));
      }
    }
    return edges;
  }

  public Graph() {
    // creates an empty graph
    createGraph(new ArrayList<Node<N>>(), new ArrayList<Edge<N,E>>());
  }

  public Graph(Collection<Node<N>> nodes, List<Edge<N,E>> edges)
  {
    createGraph(nodes, edges);
  }

  public void mergeGraph(Graph<N,E> toMerge) {
    int oldMaxID = this.maxNodeID;
    HashMap<Integer, Node<N>> newNodes = new HashMap<Integer, Node<N>>();
    for (Integer nid : toMerge.nTypes.keySet()) {
      Node<N> n = new Node<N>(oldMaxID + nid + 1, toMerge.nTypes.get(nid));
      newNodes.put(nid, n);
      this.AddNode(n);
    }
    for (Integer eid : toMerge.adjList.keySet())
      for (P<Integer, E> e : toMerge.adjList.get(eid)) {
        this.AddEdge(newNodes.get(eid), newNodes.get(e.fst()), e.snd());
      }
  }

  // simpler version where we do not rename the nodes/edges. Instead
  // just add the disjoint graph to the set we already have...
  public void mergeDisjointGraph(Graph<N,E> toMerge) {
    HashMap<Integer, Node<N>> extraNodes = new HashMap<Integer, Node<N>>();
    for (Integer nid : toMerge.nTypes.keySet()) {
      Node<N> n = new Node<N>(nid, toMerge.nTypes.get(nid));
      extraNodes.put(nid, n);
      this.AddNode(n);
    }
    for (Integer eid : toMerge.adjList.keySet())
      for (P<Integer, E> e : toMerge.adjList.get(eid)) {
        this.AddEdge(extraNodes.get(eid), extraNodes.get(e.fst()), e.snd());
      }
  }

  protected void createGraph(Collection<Node<N>> nodes, List<Edge<N,E>> edges)
  {
    this.metadata = new HashMap<String, Object>();
    // this.nodesOrig = new ArrayList<Node<N>>();
    // this.edgesOrig = new ArrayList<Edge<N,E>>();
    this.nTypes = new HashMap<Integer, N>();
    this.adjList = new HashMap<Integer, List<P<Integer, E>>>();
    this.maxNodeID = -1;

    for (Node<N> node : nodes)
      AddNode(node);

    for (Edge<N,E> e : edges)
      AddEdge(e.src, e.dst, e.bond);
  }

  /*
  @Deprecated
  public String toStringOLD()
  {
    String graph = "";
    boolean added = false;
    for (Node<N> n : this.nodesOrig)
    {
      graph += (added ? "," : "") + n;
      added = true;
    }
    graph = "Nodes[" + graph + "]; Edges:[";
    added = false;
    for (Edge<N,E> e : this.edgesOrig)
    {
      if (e.src.id > e.dst.id)
        continue; // we are looking at an undirected graph; and therefore print each edge only once.
      graph += (added ? "," : "") + e;
      added = true;
    }
    graph += "]";
    return graph;
  }
  */

  @Override
  public String toString()
  {
    String graph = "";
    boolean added = false;
    for (Integer n : this.nTypes.keySet())
    {
      graph += (added ? "," : "") + this.nTypes.get(n) + "{" + n + "}";
      added = true;
    }
    graph = "Nodes[" + graph + "]; Edges:[";
    added = false;
    for (Integer n : this.adjList.keySet())
    {
      for (P<Integer, E> e : this.adjList.get(n)) {
        if (e.fst() > n)
          continue; // we are looking at an undirected graph; and therefore print each edge only once.
        graph += (added ? "," : "") + n + e.snd() + e.fst();
        added = true;
      }
    }
    graph += "]";
    return graph;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Graph<?,?>))
      return false;
    System.err.println("Equals on a graph object? Graph isomorphism? \n"
        + "Or did you really mean strict equality? That is easy to check.\n"
        + "We do have mechanisms for checking graph isomorphism for Molecular Graphs (MolGraphs)\n"
        + "Aborting.");
    System.exit(-1);
    return false;
  }
  @Override
  public int hashCode() {
    System.err.println("Did you really intend to call a hashCode on an arbitrary graph? Aborting...");
    System.exit(-1);
    return -1;
  }

  public N GetNodeData(int id)
  {
    return this.nTypes.get(id);
  }

  public void SetNodeData(Integer id, N a) {
    this.nTypes.put(id, a);
  }

  public List<Integer> GetNodeIDs()
  {
    return new ArrayList<Integer>(this.nTypes.keySet());
  }

  public HashMap<Integer, List<Integer>> getEdgeIDs()
  {
    HashMap<Integer, List<Integer>> e = new HashMap<Integer, List<Integer>>();
    for (int s : this.adjList.keySet()) {
      List<Integer> neighbors = new ArrayList<Integer>();
      for (P<Integer, E> nn : this.adjList.get(s))
        neighbors.add(nn.fst());
      e.put(s, neighbors);
    }
    return e;
  }

  public boolean NodeExists(int nid)
  {
    return this.nTypes.containsKey(nid);
  }

  public E GetEdgeType(int n1, int n2)
  {
    List<P<Integer, E>> neighbors = this.adjList.get(n1);
    if (neighbors == null)
      return null;
    for (P<Integer, E> p : neighbors)
      if (p.fst() == n2)
        return p.snd();
    return null;
  }

  public void SetEdgeType(int n1, int n2, E e) {
    setEdgeSingularDir(n1, n2, e);
    setEdgeSingularDir(n2, n1, e);
  }

  private void setEdgeSingularDir(int n2, int n1, E e) {
    List<P<Integer, E>> neighbors = this.adjList.get(n1);
    P<Integer, E> newEdge = new P<Integer, E>(n2, e);

    if (neighbors == null) {
      neighbors = new ArrayList<P<Integer, E>>();
      neighbors.add(newEdge);
      this.adjList.put(n1, neighbors);
      return;
    }
    boolean set = false;
    for (int i=0; i<neighbors.size(); i++) {
      P<Integer, E> p = neighbors.get(i);
      if (p.fst() == n2) {
        set = true;
        neighbors.remove(i);
        neighbors.add(i, newEdge);
      }
    }
    if (!set)
      neighbors.add(newEdge);
  }
  public Graph<N, E> duplicate() {
    HashMap<Integer, Node<N>> nodes = new HashMap<Integer, Node<N>>(); // duplicate node set
    List<Edge<N,E>> edges = new ArrayList<Edge<N,E>>(); // duplicate edge set
    for (Integer nid : this.nTypes.keySet()) {
      Node<N> n = new Node<N>(nid, this.nTypes.get(nid));
      nodes.put(nid, n);
    }
    for (Integer nid : this.adjList.keySet())
      for (P<Integer, E> edge : this.adjList.get(nid)) {
        edges.add(new Edge<N,E>(nodes.get(nid), nodes.get(edge.fst()), edge.snd()));
      }
    return new Graph<N,E>(nodes.values(), edges);
  }

  public Graph<N,E> permute(HashMap<Integer, Integer> map) {
    HashMap<Integer, Node<N>> nodes = new HashMap<Integer, Node<N>>(); // oldNodeID -> new Node map
    List<Edge<N,E>> edges = new ArrayList<Edge<N,E>>(); // new edges over new nodes
    for (Integer nid : this.nTypes.keySet()) {
      Integer mapsto = map.get(nid);
      Node<N> n = new Node<N>(mapsto, this.nTypes.get(nid));
      nodes.put(nid, n);
    }
    for (Integer oldNID : this.adjList.keySet())
      for (P<Integer, E> edge : this.adjList.get(oldNID)) {
        edges.add(new Edge<N,E>(nodes.get(oldNID), nodes.get(edge.fst()), edge.snd()));
      }
    return new Graph<N,E>(nodes.values(), edges);
  }

  public Graph<N,E> subtract(Graph<N,E> other) {
    HashMap<Integer, Node<N>> nodes = new HashMap<Integer, Node<N>>(); // oldNodeID -> new Node map
    List<Edge<N,E>> edges = new ArrayList<Edge<N,E>>(); // new edges over new nodes

    // lets be conservative about deleting nodes; so lets first delete all the edges
    // that appear in 'other'... then if a node is in 'other' and also in 'this'
    // AND it does not have any edges connecting it in the new graph, then we can
    // delete it too. If on the other hand, some edge to it survived, then we should
    // remove this node from the new returned graph.

    for (Integer nid : this.adjList.keySet())

      for (P<Integer, E> edge : this.adjList.get(nid)) {

        // if this "nid" is a extra node in in "this" compared to "other"
        // then it will not have any neighbors in "other". therefore we
        // should not attempt to get its neighboring edges
        if (!other.NodeExists(nid))
          continue;
        // now we know "other" contains this "nid", therefore we can check
        // its edge to "edge.fst"
        E edgeInOther = other.GetEdgeType(nid, edge.fst());
        if (edgeInOther != null && edgeInOther.equals(edge.snd()))
          continue;

        if (!nodes.containsKey(nid))
          nodes.put(nid, new Node<N>(nid, this.nTypes.get(nid)));
        if (!nodes.containsKey(edge.fst()))
          nodes.put(edge.fst(), new Node<N>(edge.fst(), this.nTypes.get(edge.fst())));

        edges.add(new Edge<N,E>(nodes.get(nid), nodes.get(edge.fst()), edge.snd()));
      }

    // At this point, we have iterated over all edges in the old graph.
    // If an edge existed, and it also existed in 'other' we added nothing
    // If an edge existed, and it did not exist in 'other' then we added its nodes, and itself

    // The only nodes that would not have been added at this point, are the disjoint ones
    // so we iterate over all nodes, and if we find a disjoint one that does not
    // also appear in 'other' we augment 'nodes' with it
    for (Integer nid : this.nTypes.keySet()) // iterate over all the nodes in current graph
      if (!this.adjList.containsKey(nid) || this.adjList.get(nid).isEmpty()) // ensure disconnected node
        if (!other.nTypes.containsKey(nid)) // ensure that 'other' does not say "delete this node"
          nodes.put(nid, new Node<N>(nid, this.nTypes.get(nid))); //

    return new Graph<N,E>(nodes.values(), edges);
  }

  public Graph<N, E> intersect(Graph<N, E> other) {
    HashMap<Integer, Node<N>> nodes = new HashMap<Integer, Node<N>>(); // oldNodeID -> new Node map
    List<Edge<N,E>> edges = new ArrayList<Edge<N,E>>(); // new edges over new nodes

    // add all nodes that exist in both to the node set
    for (Integer nid : this.nTypes.keySet())
      if (other.nTypes.containsKey(nid) && other.nTypes.get(nid).equals(this.nTypes.get(nid)))
        nodes.put(nid, new Node<N>(nid, this.nTypes.get(nid)));

    // add all edges that exists in both graphs
    for (Integer nid : this.adjList.keySet()) {
      if (!nodes.containsKey(nid)) // if this node did not make it to the nodeset, then its adjacency is futile to consider
        continue;
      for (P<Integer, E> e : this.adjList.get(nid)) {
        if (!nodes.containsKey(e.fst())) // if the destination of this edge did not make it to the nodeset, then ignore this edge
          continue;
        E edgeInOther = other.GetEdgeType(nid, e.fst());
        if (edgeInOther != null && edgeInOther.equals(e.snd()))
          edges.add(new Edge<N,E>(nodes.get(nid), nodes.get(e.fst()), edgeInOther));
      }
    }

    return new Graph<N,E>(nodes.values(), edges);
  }

  public void removeNode(Integer nid) {
    // remove from the node list
    this.nTypes.remove(nid);

    // now remove any references in the adjacency lists
    Set<Integer> nodes = new HashSet<Integer>(this.adjList.keySet());
    for (Integer n : nodes) {
      // remove the entire adj row if the adjacency is for the node to be removed
      if (n == nid) {
        this.adjList.remove(n);
        continue;
      }
      // remove the node from any other adjacency row
      List<P<Integer, E>> adj = this.adjList.get(n);
      for (int i=0; i<adj.size(); i++) {
        if (adj.get(i).fst() == nid)
          adj.remove(i);
      }
    }
  }
}

