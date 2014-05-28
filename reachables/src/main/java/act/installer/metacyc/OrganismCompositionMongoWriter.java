package act.installer.metacyc;

import act.server.SQLInterface.MongoDB;
import java.util.HashMap;
import act.installer.metacyc.processes.Catalysis;

public class OrganismCompositionMongoWriter {
  MongoDB db;
  OrganismCompositionMongoWriter(MongoDB db) {
    System.out.println("Write to MongoDB.");
    this.db = db;
  }

  public void write(String origin, OrganismComposition o) {
    // we go through each Catalysis and Modulation, both of which refer
    // to a controller (protein/complex) and controlled (reaction)
    // for each controlled reaction we pull up its Conversion (BioCRxn, Trans, Trans+BioCRxn)
    // Conversion has left, right and other details of the reaction

    HashMap<Resource, Catalysis> cat = o.getMap(Catalysis.class);
    System.out.println("******************************************************");
    System.out.println("From file: " + origin);
    System.out.println("Extracted " + cat.size() + " catalysis observations.");
    System.out.println();
    for (Resource id : cat.keySet()) {
      Catalysis c = cat.get(id);
      // for (Resource controller : c.getController()) {
      //   System.out.println("\tControlling biomolecule: " + o.resolve(controller));
      // }
      // for (Resource controlled : c.getControlled()) {
      //   System.out.println("\tControlled reaction: " + o.resolve(controlled));
      // }
      // for (Resource cofac : c.getCofactors()) {
      //   System.out.println("\tAny Cofactors: " + o.resolve(cofac));
      // }

      // Debugging, print entire catalysis subtree:
      System.out.println(c.expandedJSON(o).toString(2));
    }

    
  }
}

