package act.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PubmedEntry {
  abstract class JSONVal {
    // a disjoint union of either List<String> or List<JSONObj>
    public abstract String toString(int indent);
  }

  class JSONValString extends JSONVal {
    // .size() == 1 implies a singular String element; not to be encoded as [ 'a' ] but just as 'a'
    List<String> jsonVal;

    public JSONValString(List<String> strVal) {
      this.jsonVal = strVal;
    }

    @Override
    public String toString(int indent) {
      // maybe it is just a list with a single element...
      String s = "\"" + escape(this.jsonVal.get(0)) + "\"";
      if (this.jsonVal.size() == 1)
        return s;

      // if OTOH it is really is a list and not just a list with a singleton element then write it as such:
      for (int i = 1; i < this.jsonVal.size(); i++)
        s += ", " + "\"" + escape(this.jsonVal.get(i)) + "\"";
      return "[" + s + "]";
    }

    private String escape(String s) {
      // This is a little complicated. replaceAll(regex, replacementStr)
      // regex for \ is \\
      // So the arguments to replaceAll have to conform to regex requirements...
      // which means the java encoding "\\" is really the string(\) which means nothing to regex
      // on the other hand the java encoding "\\\\" is really the string(\\) which is the regex representation of \

      if (s.contains("\"")) // handle the case of """. This is wrongly quoted, so we need to escape the " into "\""
        return s.replaceAll("\"", "\\\\\""); // the replacement has java_string(\\\\) = regex_string(\) followed by java_string(\")=regex(")
      if (s.contains("\\")) // char \ need to be escaped...
        return s.replaceAll("\\\\", "\\\\\\\\"); // replacement has java_string(\\\\\\\\) = regex_string(\\)
      return s;
    }
  }

  class JSONValObj extends JSONVal {
    // .size() == 1 implies a singular object element; not to be encoded as [ { k: 'v' } ] but just as { k: 'v' }
    List<JSONObj> jsonRecVal;

    public JSONValObj(List<JSONObj> nestedVal) {
      this.jsonRecVal = nestedVal;
    }

    @Override
    public String toString(int indent) {
      // maybe it is just a list with a single element...
      String s = this.jsonRecVal.get(0).toString(indent + 1);
      if (this.jsonRecVal.size() == 1)
        return s;

      // if OTOH it truly is a list of objects then write them out as a list.
      for (int i = 1; i < this.jsonRecVal.size(); i++)
        s += ", " + this.jsonRecVal.get(i).toString(indent + 1);
      return "[" + s + "]";
    }

  }

  /*
   * Encodes things such as { name: 'saurabh'; address: { num: '2319', street: 'Grant St' } }
   * Data stored will be as:
   * name -> JSONVal('saurabh')
   * address -> JSONVal( JSONObj( num -> '2319', street -> 'Grant St' ) )
   */
  class JSONObj {
    HashMap<String, JSONVal> obj;

    public JSONObj(HashMap<String, List<String>> xmlTagsVal) {
      this.obj = aggregateByKeys(xmlTagsVal);
    }

    private JSONObj(HashMap<String, JSONVal> toBeWrappedMap, boolean dummyArg) {
      this.obj = toBeWrappedMap;
    }

    private HashMap<String, JSONVal> aggregateByKeys(HashMap<String, List<String>> xml) {
      List<String> terminalKeys = new ArrayList<String>();
      HashMap<String, HashMap<String, List<String>>> subKeys = new HashMap<String, HashMap<String, List<String>>>();

      for (String k : xml.keySet()) {
        if (!k.contains("/")) { // terminal key...
          terminalKeys.add(k);
          continue;
        }
        // non-terminal key.. see if already has a map else create...
        int parentKeyLen = k.indexOf('/');
        String parent = k.substring(0, parentKeyLen);
        if (!subKeys.containsKey(parent))
          subKeys.put(parent, new HashMap<String, List<String>>());
        // add the mapping for this subkey...
        String unprefixedKey = k.substring(parentKeyLen + 1);
        subKeys.get(parent).put(unprefixedKey, xml.get(k));
      }

      HashMap<String, JSONVal> parsed = new HashMap<String, JSONVal>();

      // for terminal keys we have a direct mapping in the JSON.
      for (String k : terminalKeys)
        parsed.put(k, new JSONValString(xml.get(k)));
      // for non-terminal keys we have a recursive JSON object...
      for (String k : subKeys.keySet()) {
        String parentKey = k;
        // the projected hashMap, of the keyset within xml, that shared this parentKey is the lookup value:
        HashMap<String, List<String>> projectedMap = subKeys.get(parentKey);
        List<JSONObj> l = new ArrayList<JSONObj>();
        l.add(new JSONObj(aggregateByKeys(projectedMap), true));
        // potential bug here.... we never create a real list of JSONObjs; so we will never have
        // a : [ { k1: 'v1' } , { k1: 'v2' } ] in our final JSON....
        // TODO: why is this not a problem???? Or maybe it is; and we have a bug here...
        JSONVal subJSON = new JSONValObj(l);
        parsed.put(parentKey, subJSON);
      }
      return parsed;
    }

    @Override
    public String toString() {
      return toString(0);
    }

    private String toString(int indent) {
      String s = "";
      String indentation = new String(new char[indent]).replace("\0", "    ");
      for (String k : this.obj.keySet()) {
        s += "\n" + indentation + k + " : " + this.obj.get(k).toString(indent) + ",";
      }
      return "{" + s + "\n" + indentation + "}";
    }

    public JSONVal getXPath(int startIndex, List<String> xPath) {
      String key = xPath.get(startIndex);
      JSONVal v = this.obj.get(key);
      if (startIndex == xPath.size() - 1) {
        // end of path.
        return v;
      } else {
        if (v instanceof JSONValString) {
          // we still have fragments of a path to recurse down under; but our object here is a List<String>... so cannot dive into it...
          System.err.println("Xpath too long for this object..." + xPath);
          System.exit(-1);
        }
        JSONValObj subObj = (JSONValObj) v;
        if (subObj.jsonRecVal.size() == 1) {
          return subObj.jsonRecVal.get(0).getXPath(startIndex + 1, xPath);
        } else {
          String keyNum = xPath.get(startIndex + 1);
          int index = -1;
          try {
            index = Integer.parseInt(keyNum);
          } catch (NumberFormatException e) {
            System.err.format("Xpath needed a list index at position (%d); got path element...", startIndex + 1, xPath.toString());
            System.exit(-1);
          }
          return subObj.jsonRecVal.get(index).getXPath(startIndex + 2, xPath);
        }
      }
    }

  }

  JSONObj xmlJsonObj;

  public PubmedEntry(HashMap<String, List<String>> allXML) {
    this.xmlJsonObj = new JSONObj(allXML);
  }

  public String toJSON() {
    return this.xmlJsonObj.toString();
  }

  @Override
  public String toString() {
    return toJSON();
  }

  public JSONVal getXPath(List<String> xPath) {
    return this.xmlJsonObj.getXPath(0, xPath);
  }

  public String getXPathString(List<String> xPath) {
    JSONVal v = this.xmlJsonObj.getXPath(0, xPath);
    if (v instanceof JSONValString) {
      List<String> s = ((JSONValString) v).jsonVal;
      if (s.size() == 1)
        return s.get(0);
    }
    return null; // either list is too long or JSONValObj found here...
  }
}
