package act.server.AbstractSearch;

import java.io.File;
import java.util.ArrayList;

import act.render.RenderReactions;

import com.ggasoftware.indigo.Indigo;

import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDBPaths;
import act.shared.Chemical;
import act.shared.Reaction;

public class PathNode {
  MoleculeEquivalenceClass mec;
  Reaction r;
  ArrayList<PathNode> children;

  public PathNode(){
    this.children = new ArrayList<PathNode>();
  }

  public PathNode(MoleculeEquivalenceClass mec){
    this.mec = mec;
    this.children = new ArrayList<PathNode>();
  }

  public PathNode(Reaction r){
    this.r = r;
    this.children = new ArrayList<PathNode>();
  }

  public void addChild(PathNode pn){
    children.add(pn);
  }

  public void visualize(String folderName, MongoDBPaths DB){
    boolean success = (new File(folderName)).mkdirs();
    this.visualizeAtDepth(0, 0, folderName, DB);
  }

  public void visualizeAtDepth(int depth, int branch, String folderName, MongoDBPaths DB){
    Indigo indigo = new Indigo();
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
    for (PathNode pn : this.children){
      pn.visualizeAtDepth(depth+1,new_branch,folderName, DB);
      new_branch++;
    }
  }

}
