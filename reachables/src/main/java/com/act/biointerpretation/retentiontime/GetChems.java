package com.act.biointerpretation.retentiontime;

import act.server.MongoDB;
import act.shared.Chemical;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class GetChems {

  public static void main(String[] args) throws Exception {

    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");

    List<Chemical> chemicalList = mongoDB.getSigmaChemicals();

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File("/mnt/shared-data/Vijay/ret_time_prediction/marvin_chems.txt")))) {
      for (Chemical chemical : chemicalList) {
        predictionWriter.write(chemical.getInChI());
        predictionWriter.write("\n");
      }
    }
  }
}
