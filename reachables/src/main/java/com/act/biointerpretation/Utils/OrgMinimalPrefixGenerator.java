package com.act.biointerpretation.Utils;

import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.Map;
import java.util.SortedMap;

public class OrgMinimalPrefixGenerator {

  Map<String, String> orgNameToMinimalPrefix;

  public OrgMinimalPrefixGenerator(Map<String, Long> orgMap) {
    PatriciaTrie orgPrefixTrie = new PatriciaTrie<>(orgMap);

    while (orgPrefixTrie.size() != 0) {
      String firstKey = (String) orgPrefixTrie.firstKey();
      orgNameToMinimalPrefix.put(firstKey, firstKey);
      orgPrefixTrie.remove(firstKey);

      SortedMap<String, Long> keyPrefixMap = orgPrefixTrie.prefixMap(firstKey);

      for (String orgWithPrefix : keyPrefixMap.keySet()) {
        orgNameToMinimalPrefix.put(firstKey, orgWithPrefix);
        orgPrefixTrie.remove(orgWithPrefix);
      }
    }
  }

  public String getMinimalPrefix(String orgName) {
    return orgNameToMinimalPrefix.get(orgName);
  }

  public Map<String, String> getMinimalPrefixMapping() {
    return orgNameToMinimalPrefix;
  }

}
