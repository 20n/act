package act.installer.metacyc.processes;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.List;
import java.util.Set;

public class Pathway extends BPElement {
  Set<Resource> order;  // the order in which the components appear, a set of PathwayStep
                        // resources. These resources have a getNextStep() function that
                        // returns a Set<PathwayStep> allowing one to traverse an acyclic g
  Set<Resource> components; // catalysis or biochemicalreaction

  public Pathway(BPElement basics, Set<Resource> order, Set<Resource> components) {
    super(basics);
    this.order = order;
    this.components = components;
  }
}

