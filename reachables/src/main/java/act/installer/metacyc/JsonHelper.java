package act.installer.metacyc;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Set;
import java.util.ArrayList;

public class JsonHelper {
  OrganismComposition src;
  JSONObject json;

  public JsonHelper(OrganismComposition src) {
    this.src = src;
    this.json = new JSONObject();
  }

  public void add(String k, String v) {
    this.json.put(k, v);
  }

  public void add(String k, Set v) {
    if (v.size() > 1) {
      JSONArray a = new JSONArray();
      for (Object o : v) {
        a.put(((BPElement)o).json(this.src));
      }
    } else if (v.size() == 1) {
      Object singleton = null;
      for (Object o : v)
        singleton = o;
      if (singleton != null)
        this.json.put(k, ((BPElement)singleton).json(this.src));
      else
        this.json.put(k, "(null)");
    } else
      this.json.put(k, new JSONArray());
  }

  public JSONObject getJSON() {
    return json;
  }

}

