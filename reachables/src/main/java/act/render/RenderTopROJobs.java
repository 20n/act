package act.render;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import act.server.Molecules.CRO;
import act.server.Molecules.ERO;
import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.MongoDBPaths;

public class RenderTopROJobs {
  private MongoDBPaths mongo;
  private String outputDir;

  public RenderTopROJobs(String host, int port, String db, String dir) {
    this.mongo = new MongoDBPaths(host, port, db);
    outputDir = dir == null ? "TopROs" : dir;
  }

  private void renderTopAROs(int k) {
    List<Integer> ignoreROs = new ArrayList<Integer>();
    ignoreROs.add(0);
    List<Long> topAROs = mongo.getTopKPathSets(k,ignoreROs);
    int rank = 0;
    for(Long aro : topAROs) {
      Map<Integer,Integer> ros = mongo.getPathSet(aro);
      String dir = outputDir + "/" + "commonpathset_"+rank+"_"+aro;
      new File(dir).mkdir();
      for(Integer ro : ros.keySet()) {
        mongo.getCRO(ro).fst().render(dir+"/ro"+ro+"x"+ros.get(ro),ro+" "+ros.get(ro)+" times");
      }
      rank++;
    }
  }

  private void renderTopCROs(int k) {
    List<CRO> topCROs = mongo.getTopKCRO(k);
    int rank = 0;
    for(CRO cro : topCROs) {
      String name = "rank"+rank+"_cro"+cro.ID();
      String dir = outputDir + "/" + name + "/";
      new File(dir).mkdirs();
      cro.render(dir + name + ".png", "Rank: "+rank);
      RenderReactions.renderRxnsInCRO(cro.ID(), dir, name, 5, mongo);
      rank++;
      try{
        // Create file
        FileWriter fstream = new FileWriter(dir + name + ".txt");
        BufferedWriter out = new BufferedWriter(fstream);
        for(int rxn : mongo.getRxnsOfCRO(cro.ID())) {
          out.write(rxn+"\n");
        }
        //Close the output stream
        out.close();
      }catch (Exception e){//Catch exception if any
        System.err.println("Error: " + e.getMessage());
      }
    }
  }

  private  void renderTopEROs(int k) {
    List<ERO> topEROs = mongo.getTopKERO(k);
    int rank = 0;
    for(ERO ero : topEROs) {

      String name = "rank"+rank+"_ero"+ero.ID();
      String dir = outputDir + "/" + name + "/";
      new File(dir).mkdirs();
      ero.render(dir + name + ".png", "Rank: "+rank);
      RenderReactions.renderRxnsInERO(ero.ID(), dir, name, 20, mongo);

      rank++;
    }
  }

  public void renderTop(String type, int count) {

    if (type != null) {
      if (type.equals("CRO")) {
        renderTopCROs(count);
        return;
      } else if (type.equals("ERO")) {
        renderTopEROs(count);
        return;
      } else if (type.equals("ARO")) {
        renderTopAROs(count);
        return;
      }
    }
    // type == null || type did not match a supported one
    System.err.println("We only support RO types: {CRO, ERO, ARO}");
    System.exit(-1);
  }

  // public static void main(String[] args) {
    //renderTopAROs(10);
    //renderTopEROs(10);
    // renderTopCROs(500);
  //}
}
