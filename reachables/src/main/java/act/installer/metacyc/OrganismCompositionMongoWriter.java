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
    System.out.println("Pulling out catalysis:" + cat.size());
    for (Resource id : cat.keySet()) {
      Catalysis c = cat.get(id);
      // System.out.println(c.getID());
      // for (Resource controller : c.getController()) {
      //   System.out.println("\tControlling biomolecule: " + o.resolve(controller));
      // }
      // for (Resource controlled : c.getControlled()) {
      //   System.out.println("\tControlled reaction: " + o.resolve(controlled));
      // }
      // for (Resource cofac : c.getCofactors()) {
      //   System.out.println("\tAny Cofactors: " + o.resolve(cofac));
      // }
      System.out.println(c.json(o).toString(2));
    }

    
  }
}

