package act.installer.reachablesexplorer;


import com.act.jobs.FileChecker;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;


public class WordCloudGenerator {

  /**
   * This class allow the generation of wordclouds, using R, for any inchi having a Bing reference.
   * It requires an R script, that takes an InChI as argument and writes a word cloud to a file
   */

  private static final String RSCRIPT_LOCATION = "src/main/java/act/installer/reachablesexplorer/RWordCloudGenerator.R";

  private Runtime rt;
  private File rScript;

  private String host;
  private String port;


  public WordCloudGenerator(String host, String port) {
    this.host = host;
    this.port = port;

    rt = Runtime.getRuntime();
    rScript = new File(RSCRIPT_LOCATION);
    try {
      FileChecker.verifyInputFile(rScript);
    } catch (IOException e) {
      System.out.println("Error reading r script");
    }
  }

  public void generateWordCloud(String inchi, String filename) throws IOException {
    String cmd = String.format("Rscript %s %s %s %s %s", rScript.getAbsolutePath(), inchi, filename, host, port);

    try {
      Process p = rt.exec(cmd);
      BufferedReader in = new BufferedReader(
          new InputStreamReader(p.getInputStream()));
      String line = null;
      while ((line = in.readLine()) != null) {
        System.out.println(line);
      }
      BufferedReader err = new BufferedReader(
          new InputStreamReader(p.getErrorStream()));
      while ((line = in.readLine()) != null) {
        System.out.println(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    WordCloudGenerator g = new WordCloudGenerator("localhost", "27017");
    try {
      g.generateWordCloud("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)", "~/test-1");
    } catch (IOException e) {System.out.println(String.format("Caught expection %s", e.getMessage()));}
  }

}
