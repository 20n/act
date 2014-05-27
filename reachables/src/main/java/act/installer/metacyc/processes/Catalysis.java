package act.installer.metacyc.processes;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.biopax.paxtools.model.level3.CatalysisDirectionType;
import org.biopax.paxtools.model.level3.ControlType;
import java.util.Set;
import org.json.JSONObject;

public class Catalysis extends BPElement {
  CatalysisDirectionType direction; // RIGHT-TO-LEFT or LEFT-TO-RIGHT
  ControlType controlType; // only ACTIVATION seen, but many more exist in Enum.values(ControlType)
  Set<Resource> controller;
  Set<Resource> controlled;
  Set<Resource> cofactors;

  public Set<Resource> getController() { return this.controller; }
  public Set<Resource> getControlled() { return this.controlled; }
  public Set<Resource> getCofactors() { return this.cofactors; }

  public Catalysis(BPElement basics, CatalysisDirectionType dir, ControlType ctrlt, Set<Resource> controller, Set<Resource> controlled, Set<Resource> cofactors) {
    super(basics);
    this.direction = dir;
    this.controlType = ctrlt;
    this.controller = controller;
    this.controlled = controlled;
    this.cofactors = cofactors;
  }

  public JSONObject json(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (controlType != null) o.add("type", controlType.toString());
    if (direction != null) o.add("dir", direction.toString());
    if (controller != null) o.add("controller", src.resolve(controller));
    if (controlled != null) o.add("controlled", src.resolve(controlled));
    if (cofactors != null) o.add("cofactors", src.resolve(cofactors));
    return o.getJSON();
  }
}

