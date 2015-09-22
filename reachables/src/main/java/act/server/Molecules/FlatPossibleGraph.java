package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.graph.Edge;
import act.graph.Node;
import act.shared.helpers.BitArray;
import act.shared.helpers.P;

/*
 * This is really not used yet. It was part of the SMT implementation, which reduced graphs to their
 * adjacency matrices so that graph transformation inference was just inferring some integers
 */
class FlatPossibleGraph extends MolGraph
{
  public FlatPossibleGraph() throws Exception {
    // creates an empty graph
    super();
    this.flattened = null;
  }

  public FlatPossibleGraph(List<Node<Atom>> nodes, List<Edge<Atom, BondType>> edges) throws Exception
  {
    super(nodes, edges);
    this.flattened = null; // will be initialized later once graph is stable and frozen.
  }

  public FlatPossibleGraph(HashMap<Atom, BitArray> n, HashMap<BondType, BitArray> e, Integer sz, boolean addboth) throws Exception
  {
    initUsingMaps(n, e, sz, addboth);
    // copy the bitvectors passed in as they are the flatarray.
    this.flattened = new FlatArrays(e, n);
  }

  public void initUsingMaps(HashMap<Atom, BitArray> n, HashMap<BondType, BitArray> e, Integer sz, boolean addboth) throws Exception
  {
    // populate the nodes and edges
    HashMap<Integer, Node<Atom>> N = new HashMap<Integer, Node<Atom>>();
    for (int i = 0; i < sz; i++)
    {
      Element typ = getNodeType(n, i);
      if (typ != Element.Unknown)
        continue;
      Node<Atom> nn = new Node<Atom>(i, new Atom(typ));
      N.put(i, nn);
    }
    HashMap<Integer, Edge<Atom, BondType>> E = new HashMap<Integer, Edge<Atom, BondType>>();
    for (int i = 0; i < sz; i++)
    {
      for (int j = i + 1; j < sz; j++)
      {
        BondType typ;
        int loc = (int)(i * sz + j);
        if ((typ = getEdgeType(e, loc)) != BondType.Unknown)
          continue;
        Edge<Atom, BondType> ee = new Edge<Atom, BondType>(N.get(i), N.get(j), typ);
        E.put(loc, ee);
      }
    }
    super.createGraph(new ArrayList<Node<Atom>>(N.values()), new ArrayList<Edge<Atom, BondType>>(E.values()), addboth);
  }

  protected Element getNodeType(HashMap<Atom, BitArray> bv, int loc) throws Exception
  {
    Element index = Element.Unknown;
    for (Element k : Element.values())
    {
      if (bv.get(k).get(loc))
        if (index != Element.Unknown)
          throw new Exception("Index already assigned");
        else
          index = k;
    }
    return index;
  }

  protected BondType getEdgeType(HashMap<BondType, BitArray> bv, int loc) throws Exception
  {
    BondType index = BondType.Unknown;
    for (BondType k : BondType.values())
    {
      if (bv.get(k).get(loc))
        if (index != BondType.Unknown)
          throw new Exception("Index already assigned");
        else
          index = k;
    }
    return index;
  }

  private FlatArrays flattened; // contains all the encoded matrices of the graph

  public FlatArrays Flattened() throws Exception
  {
    // the real work is done in FreezeGraph
    if (this.flattened == null)
      throw new Exception("Graph is not frozen yet.");
    else return this.flattened;
  }

  public void AddNode(Node<Atom> node)
  {
    if (this.flattened != null) {
      System.err.println("Graph is frozen. Cannot do this now.");
      System.exit(-1);
    }
    super.AddNode(node);
  }

  public void AddEdge(Node<Atom> src, Node<Atom> dst, BondType et)
  {
    if (this.flattened != null) {
      System.err.println("Graph is frozen. Cannot do this now.");
      System.exit(-1);
    }
    super.AddEdge(src, dst, et);
  }

  public void FreezeGraph(int sizeFlatRep) throws Exception {
    // Initializes this.flattened. The graph cannot be changed thereafter. It is frozen.

    int n = (int)sizeFlatRep; int n2 = (int)(sizeFlatRep * sizeFlatRep);

    // create bit vectors to represent each node type separately.
    // int nodeTypesCount = NodeType.values().length; //  Enum.GetValues(typeof(NodeType)).Length;
    HashMap<Atom, BitArray> nodebits = new HashMap<Atom, BitArray>(); // new BitArray[nodeTypesCount];
    // for (int i = 0; i < nodeTypesCount; i++)
    //    nodebits[i] = new BitArray(n, false);
    for (Element nn : Element.values())
      nodebits.put(new Atom(nn), new BitArray(n, false));

    // create bit vectors to represent each edge type separately.
    // int edgeTypesCount = EdgeType.values().length; // Enum.GetValues(typeof(EdgeType)).Length;
    HashMap<BondType, BitArray> edgebits = new HashMap<BondType, BitArray>(); // new BitArray[edgeTypesCount];
    // for (int i = 0; i < edgeTypesCount; i++)
    //    edgebits[i] = new BitArray(n2, false);
    for (BondType ee : BondType.values())
      edgebits.put(ee, new BitArray(n2, false));

    if (MaxNodeIDContained() > n)
      throw new Exception("Cannot fit graph in this bitvector size.");
    for (int i = 0; i <= MaxNodeIDContained(); i++)
    {
      if (!Nodes().containsKey(i))
        continue;
      nodebits.get(Nodes().get(i)).Set(i, true);
      for (P<Integer, BondType> p : AdjList().get(i))
        edgebits.get(p.snd()).Set(i * n + p.fst(), true);
    }

    this.flattened = new FlatArrays(edgebits, nodebits);
  }

  public class FlatArrays
  {
    public HashMap<BondType, BitArray> Edges() { return this.edges; }
    public HashMap<Atom, BitArray> Nodes() { return this.nodes; }
    HashMap<BondType, BitArray> edges; HashMap<Atom, BitArray> nodes;
    public FlatArrays(HashMap<BondType, BitArray> edgebits, HashMap<Atom, BitArray> nodebits)
    {
      this.edges = edgebits;
      this.nodes = nodebits;
    }

    @Override
    public String toString()
    {
      String s = "Edges ";
      for (BondType i : this.edges.keySet())
        s += "(" + i + "):" + bvToString(this.edges.get(i)) + " ";
      s += "Nodes ";
      for (Atom i : this.nodes.keySet())
        s += "(" + i + "):" + bvToString(this.nodes.get(i)) + " ";
      return s;
    }

    private String bvToString(BitArray b) {
      String s = "";
      for (int i = b.length() - 1; i >= 0; i--) s += b.get(i) ? "1" : "0";
      return s;
    }
  }
}

