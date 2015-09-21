package act.render;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.List;

import act.server.VariousSearchers;
import act.server.SQLInterface.MongoDB;
import act.server.Search.SimpleConcretePath;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.Reaction;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoRenderer;

public class RenderPathways {

  /**
   * From http://stackoverflow.com/questions/11166644/creating-a-directory-whereever-jar-file-is-located-through-java
   * @return
   * @throws UnsupportedEncodingException
   */
  public static String getProgramPath2() {
    URL url = RenderPathways.class.getProtectionDomain().getCodeSource().getLocation();
    String jarPath = null;
    try {
      jarPath = URLDecoder.decode(url.getFile(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    String parentPath = new File(jarPath).getParentFile().getPath();
    return parentPath;
  }

  public static void renderCompoundImages(Path pathway, MongoDB db, String dir, String programPath) {
    Indigo indigo = new Indigo();
    IndigoInchi indigoInchi = new IndigoInchi(indigo);
    IndigoRenderer renderer = new IndigoRenderer(indigo);
    indigo.setOption("render-output-format", "png");
    //indigo.setOption("render-image-size", 400, 200);
    String targetMolName = pathway.getCompoundList().get(0).getCanon();
    String pathDir = programPath + "/" + dir + targetMolName +"/" + pathway.pathID;
    File file = new File(pathDir);
    boolean madeDir = file.mkdirs();
    int uniqueID = 1;
    while(!madeDir) {
      System.out.println("Already exists " + pathDir);
      file = new File(pathDir + "(" + uniqueID + ")");
      madeDir = file.mkdirs();
      uniqueID++;
    }

    pathDir = pathDir + "/";
    System.out.println("Path Directory: " + pathDir);
    try{
      // Create file
      FileWriter fstream = new FileWriter(pathDir+"reactions"+".txt");
      BufferedWriter out = new BufferedWriter(fstream);
      int i = 0;
      Chemical reactant = null, product = null;
      IndigoObject indigoCompound;
      for (Chemical compound : pathway.getCompoundList()) {
        String smiles = compound.getSmiles();
        String inchi = compound.getInChI();
        System.err.format("Loading mol %s Inchi[%s] SMILES[%s]\n", compound.getCanon(), inchi, smiles);
        if(smiles == null) {
          try {
            indigoCompound = indigoInchi.loadMolecule(inchi);
          } catch (Exception e) {
            System.err.println("failed to load " + inchi);
            indigoCompound = indigo.loadMolecule("");
            e.printStackTrace();
          }
        } else {
          indigoCompound = indigo.loadMolecule(smiles);
        }

        indigo.setOption("render-comment", compound.getCanon() + " " + compound.getUuid());

        renderer.renderToFile(indigoCompound, pathDir + i + ".png");

        if(i == 0) {
          reactant = compound;
          i++;
          continue;
        }
        product = compound;

        //actual reaction
        out.write("\n\nStep " + i + "\n");
        List<Long> rxns = db.getRxnsWith(reactant.getUuid(), product.getUuid());
        rxns.addAll(db.getRxnsWith(product.getUuid(), reactant.getUuid()));
        for(Long rxn : rxns) {
          out.write(rxn+"\t");
          Reaction r = db.getReactionFromUUID(rxn);
          out.write(r.getReactionName() + "\t");
          out.write(r.getECNum() + "\n");
        }
        reactant = compound;
        i++;

      }
      i = 0;
      List<Long> rxns = pathway.getReactionIDList();
      if (rxns!=null) {
        for (Long rxnID : rxns) {
          if (rxnID != null) //to be really safe
            RenderReactions.renderByRxnID(rxnID, pathDir+"rxnID_"+i+".png", null, db);
          i++;
        }
      }
      //Close the output stream
      out.close();
    }catch (Exception e){//Catch exception if any
      e.printStackTrace();
    }
  }

  /**
   * Args: startname endname max_number_of_paths max_path_length out_directory
   */
  public static void main(String[] args) {
    MongoDB db = new MongoDB();
    String dir = "pathways";
    Long startID = (long)32669;
    Long endID = (long)13737;
    int numPaths = 200;
    int maxDepth = 6;
    String topLevelDir = getProgramPath2();

    if(args.length == 5) {
      String startName = args[0];
      String endName = args[1];
      startID = db.getChemicalIDFromName(startName);
      endID = db.getChemicalIDFromName(endName);
      numPaths = Integer.parseInt(args[2]);
      maxDepth = Integer.parseInt(args[3]);
      dir = args[4];
      System.out.println("Searching paths between " + startID + " " + endID);
      SimpleConcretePath scp = new SimpleConcretePath();
      scp.findSimplePaths(startID, endID,numPaths,maxDepth);
      for(Path p : scp.getPaths())
        renderCompoundImages(p, db, dir, topLevelDir);
    } else if(args.length == 4){
      String target = args[0];
      endID = db.getChemicalIDFromName(target);
      //PathBFS bfs = new PathBFS(db,PathBFS.getNativeMetaboliteIDs(db));
      //List<Path> paths = bfs.getPaths(endID);
      numPaths = Integer.parseInt(args[1]);
      maxDepth = Integer.parseInt(args[2]);
      dir = args[3];
      SimpleConcretePath scp = new SimpleConcretePath();
      HashSet<Long> metabolites = VariousSearchers.chemicalsToIDs(db.getNativeMetaboliteChems());
      scp.findSimplePaths(endID, metabolites,numPaths,maxDepth);
      for(Path p : scp.getPaths())
        renderCompoundImages(p, db, dir, topLevelDir);
    } else {

    }
  }
}
