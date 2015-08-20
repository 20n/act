package com.twentyn.patentExtractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;

public class HTMLExtractor {

  public void extract(File inputFile) throws IOException {
    Document jsoupDoc = Jsoup.parse(inputFile, "utf-8");
    jsoupDoc.outputSettings().indentAmount(4);
    jsoupDoc.outputSettings().prettyPrint(true);
    //System.out.println(jsoupDoc.html());
    Elements claims = jsoupDoc.body().select("div.patent-claims-section");
    System.out.println(claims.toString());
  }

  public static void main(String[] args) throws Exception {
    HTMLExtractor extractor = new HTMLExtractor();
    extractor.extract(new File(args[0]));
  }
}
