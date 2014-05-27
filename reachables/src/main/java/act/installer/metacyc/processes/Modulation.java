package act.installer.metacyc.processes;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.biopax.paxtools.model.level3.ControlType;
import java.util.Set;
import org.json.JSONObject;

public class Modulation extends BPElement {
  ControlType controlType; // e.g., INHIBITION, fullset: Enum.values(ControlType)
  Set<Resource> controller;
  Set<Resource> controlled;

  public Modulation(BPElement basics, ControlType ctrlt, Set<Resource> controller, Set<Resource> controlled) {
    super(basics);
    this.controlType = ctrlt;
    this.controller = controller;
    this.controlled = controlled;
  }

  public JSONObject json(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("type", controlType.toString());
    o.add("controller", src.resolve(controller));
    o.add("controlled", src.resolve(controlled));
    return o.getJSON();
  }
}

