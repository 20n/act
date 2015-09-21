package act.server.AbstractSearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.experimental.isomorphism.*;
import org.jgrapht.experimental.equivalence.*;
import org.jgrapht.graph.SimpleGraph;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import act.shared.Chemical;

public class CarbonSkeleton {
  UndirectedGraph<Node,Edge> g;
  final int hashCodeInt;
  private int loopCounter = 1;
  ArrayList<String> smiles;
  ArrayList<String> inchi;
  final public String inchiString;
  String smilesID;

  public CarbonSkeleton(Chemical c) {
    this.g = graphFromChemical(c);
    initialize();
    this.inchiString = this.inchi.toString();
    this.hashCodeInt = this.inchiString.hashCode();
  }

  public CarbonSkeleton(String smiles) {
    this.g = graphFromSMILES(smiles);
    initialize();
    this.inchiString = this.inchi.toString();
    this.hashCodeInt = this.inchiString.hashCode();
  }

  public void setSmilesID(int i) {
    Boolean first = true;
    String wholeSmiles = i+"_";
    for (String oneSmiles : this.smiles){
      if (first){
        wholeSmiles = wholeSmiles+oneSmiles;
        first = false;
      }
      else {
        wholeSmiles=wholeSmiles+"K";
        wholeSmiles=wholeSmiles+oneSmiles;
      }
    }
    this.smilesID = wholeSmiles;
  }

  private void initialize(){
    this.smiles = carbonSkeletonSmilesFromGraph(this.g);
    this.inchi = new ArrayList<String>();
    for (String s : this.smiles){
      inchi.add(inchiFromSmiles(s));
    }
    Collections.sort(inchi,new Comparator<String>() {
            public int compare(String string1, String string2) {
                return string1.compareTo(string2);
            }
        });
  }

  private class Node {
    Integer index;
    public String label = "C";
    boolean visited = false;

    public Node(Integer i){
      this.index = i;
    }

    public String toString(){
      return Integer.toString(index);
    }
  }

  private class Edge {
    Integer weight;
    public String label;
    boolean visited = false;

    public Edge(Integer i) {
      this.weight = i;
      this.label = bondOrderToLabel(i);
    }

    public String bondOrderToLabel(int order){
            switch (order) {
              case 1: return "";
              case 2: return "=";
              case 3: return "#";
              default: System.err.println("edge weight too big"); System.exit(-1); return "problem";
            }
    }

    public String toString(){
      return Integer.toString(weight);
    }
  }

  static class NodeComparator implements EquivalenceComparator<Node, Graph<Node,Edge>>
  {
    @Override
    public boolean equivalenceCompare(Node arg0, Node arg1,
        Graph<Node, Edge> arg2, Graph<Node, Edge> arg3) {
      return true;
    }

    @Override
    public int equivalenceHashcode(Node arg0, Graph<Node, Edge> arg1) {
      // TODO Auto-generated method stub
      return 0;
    }
  }

  static class EdgeComparator implements EquivalenceComparator<Edge, Graph<Node,Edge>>
  {
    @Override
    public boolean equivalenceCompare(Edge arg0, Edge arg1,
        Graph<Node, Edge> arg2, Graph<Node, Edge> arg3) {
      return arg0.weight == arg1.weight;
    }

    @Override
    public int equivalenceHashcode(Edge arg0, Graph<Node, Edge> arg1) {
      // TODO Auto-generated method stub
      return 0;
    }

  }

  public String toString() {
    //return this.g.vertexSet().toString()+"---"+this.g.edgeSet().toString();
    return this.inchiString;
  }

  public String hashCodeString(){
    int nodes = this.g.vertexSet().size();
    Set<Edge> edges = this.g.edgeSet();
    int order1 = 0;
    int order2 = 0;
    int order3 = 0;
    for (Edge e : edges){
            switch (e.weight) {
              case 1: order1++; break;
              case 2: order2++; break;
              case 3: order3++; break;
              default: System.err.println("edge weight too big"); System.exit(-1);
            }
    }
    return (nodes+"-"+order1+"-"+order2+"-"+order3);
  }

  private String atomToPositionString(IndigoObject atom){
    float[] position = atom.xyz();
    return Float.toString(position[0])+"-"+Float.toString(position[1])+"-"+Float.toString(position[2]);
  }


  @Override
  public int hashCode(){
    return this.hashCodeInt;
  }


  @Override
  public boolean equals(Object o){
    if (o instanceof CarbonSkeleton){
      return (this.toString().equals(o.toString()));
    }
    else{
      return false;
    }
  }
  /*
  public boolean equals(Object o){
    if (o instanceof CarbonSkeleton){
      System.out.println(this);
      System.out.println(o);
      return ((this.hashCode() == o.hashCode()) && (this.isIsomorphic((CarbonSkeleton) o)));
    }
    else{
      return false;
    }
  }
  */

  public boolean isIsomorphic(CarbonSkeleton cS){
    try{
    GraphIsomorphismInspector iso = AdaptiveIsomorphismInspectorFactory.createIsomorphismInspector(this.g, cS.g, new NodeComparator(), new EdgeComparator());
    /*
    System.out.println("about to try iso.isisomorphic");
    System.out.println(this);
    System.out.println(cS);
    */
    //TODO get rid of this and fix it

      boolean isoResult = iso.isIsomorphic();
      return isoResult;
    }
    catch(Exception e){
      System.out.println("Failed to complete isomorphism comparison:");
      System.out.println(this);
      System.out.println(cS);
      return false;
    }
  }

  private UndirectedGraph<Node,Edge> graphFromSMILES(String s){
    Indigo indigo = new Indigo();
    IndigoObject mol = null;
    try{
      mol = indigo.loadMolecule(s);
    }
    catch(IndigoException e){
      //String inchi = c.getInChI().split("=")[1];
      //System.out.println(inchi);
      //mol = indigo.loadQueryMolecule(inchi);
      System.out.println("Failed to load molecule");
      return null;
    }

    UndirectedGraph<Node,Edge> g = new SimpleGraph<Node,Edge>(Edge.class);
    HashMap<Integer,Node> atomDict = new HashMap<Integer,Node>();
    Integer counter = 0;

    for (IndigoObject atom : mol.iterateAtoms()){
      //System.out.println(atom.symbol());
      if (atom.symbol().equals("C")){
        Node n = new Node(counter);
        g.addVertex(n);
        atomDict.put(atom.index(),n);
        counter++;
      }
    }

    for (IndigoObject bond : mol.iterateBonds()){
      if (atomDict.containsKey(bond.source().index()) && atomDict.containsKey(bond.destination().index())){
        g.addEdge(atomDict.get(bond.source().index()),atomDict.get(bond.destination().index()),new Edge(bond.bondOrder()));
      }
    }
    return g;
  }

  private ArrayList<String> carbonSkeletonSmilesFromGraph(UndirectedGraph<Node,Edge> g){
    ArrayList<String> smiles = new ArrayList<String>();
    for (Node n : g.vertexSet()){
      if (n.visited){
        //if we've already visited the node, no need to spin off a new DFS
        //we've found it in another DFS
        continue;
      }
      //else, spin off a new DFS
      this.loopCounter = 1;
      smiles.add(SmilesFromNode(g,n,false));
    }
    return smiles;
  }

  private String SmilesFromNode(UndirectedGraph<Node,Edge> g, Node n, boolean includeEdgeOrder){
    n.visited = true;
    ArrayList<String> partialSmilesList = new ArrayList<String>();
    for (Edge e : g.edgesOf(n)){
      if (!e.visited){
        e.visited = true;
        Node n2 = Graphs.getOppositeVertex(g,e,n);
        if (n2.visited){
          //the current edge closes a cycle, so we have to update n to show that
          //we'll change the label so that our loops will have counters, the way smiles does loops
          n2.label = n2.label+this.loopCounter;
          n.label = n.label+this.loopCounter;
          this.loopCounter++;
        }
        else {
          String partialSmiles = "";
          if (includeEdgeOrder){
            partialSmiles+=e.label;
          }
          partialSmiles += SmilesFromNode(g,n2,includeEdgeOrder); //note that we add the edge label in front
          partialSmilesList.add(partialSmiles);
        }
      }
    }
    String smiles = n.label;
    int length = partialSmilesList.size();
    //here we'll add parentheses for any partialSmiles that constitutes a branch
    for (int i=0; i < length; i++){
      if (i==(length-1)){
        smiles+=partialSmilesList.get(i);
      }
      else{
        smiles+="("+partialSmilesList.get(i)+")";
      }
    }
    return smiles;
  }

  private UndirectedGraph<Node,Edge> graphFromChemical(Chemical c){
    return graphFromSMILES(c.getSmiles());
  }

  private String inchiFromSmiles(String smiles){
    Indigo indigo = new Indigo();
    IndigoObject molecule = indigo.loadMolecule(smiles);
      IndigoInchi ic = new IndigoInchi(indigo);
    String inchi = ic.getInchi(molecule);
    return inchi;
  }

}
