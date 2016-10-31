package com.act.biointerpretation.retentiontime;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import com.mongodb.DBObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class GetChems {

  public static void main(String[] args) throws Exception {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");
    DBIterator chemIter = mongoDB.getIteratorOverChemicals();

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File("/mnt/shared-data/Vijay/ret_time_prediction/marvin_all_chems.txt")))) {
      while(chemIter.hasNext()) {
        DBObject chemObj = chemIter.next();
        Chemical chem = mongoDB.convertDBObjectToChemical(chemObj);
        predictionWriter.write(chem.getInChI());
        predictionWriter.write("\n");
      }
    }
  }
}
