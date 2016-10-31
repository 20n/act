package com.act.biointerpretation.retentiontime;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import com.mongodb.DBObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class GetChems {

  public static void main(String[] args) throws Exception {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");
    DBIterator chemIter = mongoDB.getIteratorOverChemicals();

    Set<Integer> indexes = new HashSet<>();
    Random random = new Random(10);

    for (int i = 0; i < 5000; i++) {
      indexes.add(random.nextInt(943622));
    }

    int counter = 0;

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File("/mnt/shared-data/Vijay/ret_time_prediction/marvin_all_chems_random.txt")))) {
      while(chemIter.hasNext()) {
        if (indexes.contains(counter)) {
          DBObject chemObj = chemIter.next();
          Chemical chem = mongoDB.convertDBObjectToChemical(chemObj);
          predictionWriter.write(chem.getInChI());
          predictionWriter.write("\n");
        }
        counter++;
      }
    }
  }
}
