package act.server.AbstractSearch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import act.render.RenderReactions;

import com.ggasoftware.indigo.Indigo;

import act.server.AbstractSearch.AbstractReactionsHypergraph.IdType;
import act.server.Molecules.RO;
import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDBPaths;
import act.server.Search.ReactionsHypergraph;
import act.shared.Chemical;
import act.shared.Reaction;


public class MultiplePathNode {
  Chemical target;
  MoleculeEquivalenceClass mec;
  Reaction r;
  List<List<MultiplePathNode>> children = new ArrayList<List<MultiplePathNode>>();
  int roid;
  String moleculeInchi;
  CarbonSkeleton targetSkeleton;
  List<CarbonSkeleton> reactantSkeletons;
  String reactionString;
  String targetSmiles;
  List<String> reactantSmiles;
  RO ro;
  IdType idType;

  public MultiplePathNode(){
  }

  public MultiplePathNode(int roid){
    this.roid = roid;
  }

  public MultiplePathNode(String moleculeInchi){
    this.moleculeInchi = moleculeInchi;
  }

  public MultiplePathNode(MoleculeEquivalenceClass mec){
    this.mec = mec;
  }

  public MultiplePathNode(Reaction r){
    this.r = r;
  }

  public void carbonSkeletonNode(){
    this.idType = IdType.CARBON_SKELETON;
  }

  public void eroNode(){
    this.idType = IdType.ERO;
  }

  public void setDisplayStrings(String targetSmiles, String reactionString, List<String> reactantSmiles){
    this.reactionString = reactionString;
    this.targetSmiles = targetSmiles;
    this.reactantSmiles = reactantSmiles;
  }

  public MultiplePathNode(Chemical target){
    this.target = target;
  }

  public void addChild(List<MultiplePathNode> pn){
    if (pn == null){
      System.out.println("Tried to add null child here:");
      Thread.dumpStack();
    }
    this.children.add(pn);
  }

  public void addChild(MultiplePathNode pn){
    if (pn == null){
      System.out.println("Tried to add null child here:");
      Thread.dumpStack();
    }
    List<MultiplePathNode> pnls = new ArrayList<MultiplePathNode>();
    pnls.add(pn);
    this.children.add(pnls);
  }

  public void render(String folderName, MongoDBPaths DB){
    List<AbstractReactionsHypergraph> hypergraphLs = new ArrayList<AbstractReactionsHypergraph>();
    List<NodeSet> allFeasibleNodeSubsets = this.getAllFeasibleNodeSubsets();
    int counter = 0;
    for (NodeSet ns : allFeasibleNodeSubsets){
      AbstractReactionsHypergraph hypergraph = new AbstractReactionsHypergraph();
      ArrayList<MultiplePathNode> nodes = ns.nodes;
      for (MultiplePathNode node : nodes){
        if (node.idType == IdType.CARBON_SKELETON || node.idType == IdType.ERO){
          hypergraph.setIdType(node.idType);
          List<String> reactionIDList = new ArrayList<String>();
          reactionIDList.add(node.reactionString);
          hypergraph.addReactions(node.targetSmiles, reactionIDList);
          hypergraph.addReactants(node.reactionString, node.reactantSmiles);
        }
      }
      hypergraphLs.add(hypergraph);
      try {
        hypergraph.writeDOT(folderName, "/hypergraph"+counter+".dot", DB, true);
      }
      catch (Exception e) {
        System.out.println(e);
        e.printStackTrace();
      }
      counter++;
    }
  }

  private class NodeSet {
    ArrayList<MultiplePathNode> nodes;

    public NodeSet(ArrayList<MultiplePathNode> nodes){
      this.nodes = nodes;
    }
  }

  public List<NodeSet> getAllFeasibleNodeSubsets() {
    //System.out.println("---start call");
    ArrayList<MultiplePathNode> nodeList = new ArrayList<MultiplePathNode>();
    nodeList.add(this);
    NodeSet nodeSet = new NodeSet(nodeList);
    List<NodeSet> frozenNodeSetList = new ArrayList<NodeSet>();
    List<NodeSet> currChildNodeSetList = new ArrayList<NodeSet>();
    frozenNodeSetList.add(nodeSet);

    //System.out.println(this.children.size()+" children");
    for (List<MultiplePathNode> child : this.children){
      currChildNodeSetList = new ArrayList<NodeSet>();
      for (MultiplePathNode oneOption : child) {
        //System.out.println(child.size()+" options for child");
        //System.out.println(frozenNodeSetList.size()+" items in frozenNodeSetList");
        List<NodeSet> childSets = oneOption.getAllFeasibleNodeSubsets();
        //System.out.println(childSets.size()+" subsets for option");
        for (NodeSet childSet : childSets){
          for (NodeSet frozenNodeSet : frozenNodeSetList){
            //System.out.println("new frozenNodeSet, childOptionSet combination");
            //for each way of doing the child, for each nodeset we already have, make a combination of the way of doing the child and the preexisting
            ArrayList<MultiplePathNode> newNodeList = (ArrayList) frozenNodeSet.nodes.clone();
            newNodeList.addAll(childSet.nodes);
            currChildNodeSetList.add(new NodeSet(newNodeList));
          }
        }
      }
      //System.out.println(currChildNodeSetList.size()+" sets after child");
      frozenNodeSetList = currChildNodeSetList;
    }
    //System.out.println(frozenNodeSetList.size()+" sets at end");
    //System.out.println("---end call");
    return frozenNodeSetList;
  }

  public void renderRecurse(List<ReactionsHypergraph> hypergraphLs, ReactionsHypergraph currHypergraph){
    if (this.r != null){
      List<String> reactionIDList = new ArrayList<String>();
      String reactionID = this.r.getUUID()+"";
      reactionIDList.add(reactionID);
      String targetSmiles = this.targetSkeleton.smiles.get(0);
      List<String> reactantSmiles = new ArrayList<String>();
      for (CarbonSkeleton cs : this.reactantSkeletons){
        reactantSmiles.add(cs.smiles.get(0));
      }

      currHypergraph.addReactions(targetSmiles, reactionIDList);
      currHypergraph.addReactants(reactionID, reactantSmiles);
      List<Long> chemicals = new ArrayList<Long>();
    }
  }

  public void visualize(String folderName, MongoDBPaths DB){
    boolean success = (new File(folderName)).mkdirs();
    this.visualizeAtDepth(0, 0, folderName, DB);
  }

  public void visualizeAtDepth(int depth, int branch, String folderName, MongoDBPaths DB){
    Indigo indigo = new Indigo();
    if (this.target != null){
      SMILES.renderMolecule(indigo.loadMolecule(this.target.getSmiles()), folderName+"/"+depth+"_target_"+this.target.getUuid()+".png", this.target.getUuid().toString(), indigo);
    }
    if (this.r != null){
      RenderReactions.renderByRxnID((long) this.r.getUUID(), folderName+"/"+depth+"_"+branch+"_reaction.png", null, DB);
    }
    if (this.mec != null){
      int counter = 0;
      for (Long uuid : this.mec.matchingChemicalUUIDs){
        Chemical c = DB.getChemicalFromChemicalUUID(uuid);
        SMILES.renderMolecule(indigo.loadMolecule(c.getSmiles()), folderName+"/"+depth+"_"+branch+"_chemical_"+counter+"_"+uuid+".png", uuid.toString(), indigo);
        counter++;
      }
    }
    int new_branch = 0;
    for (List<MultiplePathNode> pnList: this.children) {
      for (MultiplePathNode pn : pnList){
        pn.visualizeAtDepth(depth+1,new_branch,folderName, DB);
        new_branch++;
      }
    }
  }

}
