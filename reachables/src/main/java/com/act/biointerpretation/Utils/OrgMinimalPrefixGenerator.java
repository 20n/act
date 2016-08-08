package com.act.biointerpretation.Utils;

import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public class OrgMinimalPrefixGenerator {

  Map<String, String> orgNameToMinimalPrefix;

  public OrgMinimalPrefixGenerator(Map<String, Long> orgMap) {
    PatriciaTrie orgPrefixTrie = new PatriciaTrie<>(orgMap);
    orgNameToMinimalPrefix = new HashMap<>();

    while (orgPrefixTrie.size() != 0) {
      String firstKey = (String) orgPrefixTrie.firstKey();
      orgNameToMinimalPrefix.put(firstKey, firstKey);
      orgPrefixTrie.remove(firstKey);

      SortedMap<String, Long> keyPrefixMap = orgPrefixTrie.prefixMap(firstKey);

      List<String> namesToRemove = new ArrayList<>();

      for (String orgWithPrefix : keyPrefixMap.keySet()) {
        orgNameToMinimalPrefix.put(orgWithPrefix, firstKey);
        namesToRemove.add(orgWithPrefix);
      }

      for (String nameToRemove : namesToRemove) {
        orgPrefixTrie.remove(nameToRemove);
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
