package com.twentyn.patentScorer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

public class Utils {

  public static String GetPatentText(String id) throws IOException {
    return fetch("https://www.google.com/patents/" + id);
  }

  public static String fetch(String link) throws IOException {
    URL url = new URL(link);
    String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.76 Safari/537.36";
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent", USER_AGENT);

    int respCode = conn.getResponseCode();
    System.err.println("\nSearch Sending 'GET' request to URL : " + url);
    System.err.println("Response Code : " + respCode);

    if (respCode != 200) {
      throw new IOException("StatusCode = " + respCode + " - GET returned not OK.\n" + url);
    }

    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuffer resp = new StringBuffer();
    String inputLine;
    while ((inputLine = in.readLine()) != null)
      resp.append(inputLine);
    in.close();

    return resp.toString();
  }

  // NOT USED
  private static String readPatentFromDisk(String id) throws IOException {
    String out = Utils.readFile("patents" + "/" + id.substring(0,4) + "/" + id + ".txt");;
    if (out == null || out.isEmpty()) {
      return null;
    }
    return out;
  }


  // NOT USED
  private static void savePatentToDisk(String id, String text, int score) {
    // Wierd! What does saving to disk have to do with CUTOFF_SCORES!
    // final int CUTOFF_SCORE = 700;
    // if(score < CUTOFF_SCORE) {
    //   text = Integer.toString(score);
    // }
    File dir = new File("patents");
    if(!dir.exists()) {
      dir.mkdir();
    }
    File subdir = new File("patents" + "/" + id.substring(0,4));
    if(!subdir.exists()) {
      subdir.mkdir();
    }
    String filename = "patents" + "/" + id.substring(0,4) + "/" + id + ".txt";
    String filepath = new File(filename).getAbsolutePath();
    Utils.writeFile(text, filepath);
  }

  public static boolean filesPresentIn(String dir) {
    File dirf = new File(dir);
    return dirf.isDirectory() && dirf.listFiles().length > 0;
  }

  public static String readFile(String path) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(path))));
    String line;
    StringBuffer sb = new StringBuffer();
    while ((line = br.readLine()) != null) {
      sb.append(line);
    }
    return sb.toString();
  }

  public static void writeFile(String datafile, String filePath) {
    try {
      Writer output = null;
      File file = new File(filePath);
      output = new FileWriter(file);
      output.write(datafile);
      output.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public static void delay(int seconds) {
    try {
      // long rand = 0;
      // while(rand < seconds*1000 || rand > seconds*2000) {
      //     rand = (long) (Math.random()*seconds*4000);
      // }
      long ms = seconds * (1000 + ((long) Math.random() * 1000));
      Thread.sleep(ms);
    } catch (InterruptedException ex) {
    }
  }

}
