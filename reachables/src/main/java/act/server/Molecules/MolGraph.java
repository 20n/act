package act.server.Molecules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;

import act.graph.Graph;
import act.graph.Node;
import act.graph.Edge;
import act.shared.helpers.P;
import act.shared.helpers.T;


public class MolGraph // wrapper class for Graph<NodeType, EdgeType>
{
  private final Graph<Atom, BondType> g;

  public HashMap<Integer, List<P<Integer, BondType>>> AdjList() { return this.g.AdjList(); }
  public HashMap<Integer, Atom> Nodes() { return this.g.Nodes(); }
  public Set<T<Integer, Integer, BondType>> ComputeEdges() { return this.g.computeEdgeSetFromAdjList(); }

  private MolGraph(Graph<Atom, BondType> g) {
    this.g = g;
  }

  public MolGraph() {
    this.g = new Graph<Atom, BondType>();
  }

  public MolGraph(Collection<Node<Atom>> nodes, List<Edge<Atom, BondType>> edges)
  {
    this.g = new Graph<Atom, BondType>(nodes, edges);
  }

  public MolGraph duplicate() {
    return new MolGraph(this.g.duplicate());
  }

  public MolGraph permute(HashMap<Integer, Integer> extendedMap) {
    return new MolGraph(this.g.permute(extendedMap));
  }

  public MolGraph intersect(MolGraph g2) {
    return new MolGraph(this.g.intersect(g2.g));
  }

  public MolGraph subtract(MolGraph g2) {
    return new MolGraph(this.g.subtract(g2.g));
  }

  public void removeNode(Integer nodeId) {
    this.g.removeNode(nodeId);
  }

  public List<Integer> GetNodeIDs() {
    return this.g.GetNodeIDs();
  }

  public boolean NodeExists(int nid) {
    return this.g.NodeExists(nid);
  }

  public void mergeGraph(MolGraph gg) {
    this.g.mergeGraph(gg.g);
  }

  public void mergeDisjointGraph(MolGraph gg) {
    this.g.mergeDisjointGraph(gg.g);
  }

  public HashMap<Integer, List<Integer>> getEdgeIDs() {
    return this.g.getEdgeIDs();
  }

  public Atom GetNodeType(int id) {
    return this.g.GetNodeData(id);
  }

  public void SetNodeData(Integer id, Atom a) {
    this.g.SetNodeData(id, a);
  }

  public BondType GetEdgeType(int n1, int n2) {
    return this.g.GetEdgeType(n1, n2);
  }

  public void SetEdgeType(int n1, int n2, BondType b) {
    this.g.SetEdgeType(n1, n2, b);
  }

  public int edge2IndigoBondNum(int n1, int n2)
  {
    switch (this.g.GetEdgeType(n1, n2))
    {
    case Single: return 1;
    case Double: return 2;
    case Triple: return 3;
    default: return 0;
    }
  }

  // The following is needed in SMILES for balancing reactions
  public int MaxNodeIDContained() {
    return this.g.MaxNodeIDContained();
  }

  public void CreateNewNodeAndAddEdgeTo(Integer id, Integer newid, Atom newnode, BondType bt) {
    this.g.CreateNewNodeAndAddEdgeTo(id, newid, newnode, bt);
  }

  public void AddNode(Node<Atom> node) {
    this.g.AddNode(node);
  }
  public void AddEdge(Node<Atom> src, Node<Atom> dst, BondType et) {
    this.g.AddEdge(src, dst, et);
  }
  public void createGraph(ArrayList<Node<Atom>> nodes,
      ArrayList<Edge<Atom, BondType>> edges, boolean addboth) {
    this.createGraph(nodes, edges, addboth);
  }

  @Override
  public String toString() {
    return this.g.toString();
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MolGraph))
      return false;
    Indigo lib = new Indigo();
    return SMILES.FromGraphWithoutUnknownAtoms(lib, this).equals(SMILES.FromGraphWithoutUnknownAtoms(lib, (MolGraph)o));
  }
  @Override
  public int hashCode() {
    Indigo lib = new Indigo();
    return SMILES.FromGraphWithoutUnknownAtoms(lib, this).hashCode();
  }
  public boolean queryEquals(Object o) {
    if (!(o instanceof MolGraph))
      return false;
    Indigo lib = new Indigo();
    return SMILES.FromGraphWithUnknownAtoms(lib, this).equals(SMILES.FromGraphWithUnknownAtoms(lib, (MolGraph)o));
  }
  public int queryHashCode() {
    Indigo lib = new Indigo();
    return SMILES.FromGraphWithUnknownAtoms(lib, this).hashCode();
  }

}
