package com.twentyn.patentExtractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;

public class HTMLExtractor {

  public void extract(File inputFile) throws IOException {
    Document jsoupDoc = Jsoup.parse(inputFile, "utf-8");
    jsoupDoc.outputSettings().indentAmount(4);
    jsoupDoc.outputSettings().prettyPrint(true);
    System.out.println(jsoupDoc.html());
  }

  public static void main(String[] args) throws Exception {
    HTMLExtractor extractor = new HTMLExtractor();
    extractor.extract(new File(args[0]));
  }
}
