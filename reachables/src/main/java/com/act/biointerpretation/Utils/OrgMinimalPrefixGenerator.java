/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.Utils;

import act.shared.Organism;
import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public class OrgMinimalPrefixGenerator {

  Map<String, String> orgNameToMinimalPrefix;

  /*
  We initialize a prefix tree using the PatriciaTrie API with all the organism names in our database. The API orders the
  strings lexicographically. The API also provides us a prefixMap function that creates a SortedMap of all strings that
  have the passed in string as a prefix. Hence, every string in a prefix map of a given string can be assigned that
  original string as a minimal prefix.

  In a lexicographic ordering, a prefix string must come before a longer string that has that prefix. This ensures that
  when we generate prefix maps in lexicographic order, we will be assigning the smallest length prefix for all organism
  names.
   */
  public OrgMinimalPrefixGenerator(Iterator<Organism> orgIterator) {
    Map<String, Long> orgMap = new HashMap<>();

    while (orgIterator.hasNext()) {
      Organism org = orgIterator.next();
      orgMap.put(org.getName(), 1L);
    }

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

  public Map<String, String> getMinimalPrefixMapping() {
    return orgNameToMinimalPrefix;
  }

}
