package com.act.analysis.surfactant;

import chemaxon.formats.MolImporter;
import com.act.utils.TSVParser;

import java.io.File;
import java.util.List;
import java.util.Map;

public class FeatureExtractor {

  public static void main(String[] args) throws Exception {

    TSVParser parser = new TSVParser();
    parser.parse(new File(""));
    List<String> header = parser.getHeader();


    for (Map<String, String> row : parser.getResults()) {
      





    }





  }




}
