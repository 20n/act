/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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

/**
 * These utility methods were extracted from the experimental repo, and were originally intended for use with
 * PatentMiner/src/org/twentyn/patentminer/GoogleSearcher.java.
 */
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
}
