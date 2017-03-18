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

package act.installer.brenda;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import static org.junit.Assert.assertEquals;


public class BrendaChebiOntologyTest {

  @Test
  public void testApplicationToMainApplicationMapping() {

    Map<String, Set<String>> isSubtypeOfRelationships = new HashMap<>();

    /* The following schema describes the setup for this test, where line describe a "is subtype of" relationship
    between the top application and the bottom application

             1 -> main application ontology
            / \
           2   3 -> these are main applications
          /     \
         4       5 -> these are sub applications
          \     / \
           \   /   6 -> another level of sub-applications
            \ /     \
             7       8 -> final level of sub-applications
    */

    // 2 and 3 are main applications (therefore subtypes of the main application id 1)
    isSubtypeOfRelationships.put("1", new HashSet<>(Arrays.asList("2", "3")));

    // sub applications
    isSubtypeOfRelationships.put("2", Collections.singleton("4"));
    isSubtypeOfRelationships.put("3", Collections.singleton("5"));
    isSubtypeOfRelationships.put("5", new HashSet<>(Arrays.asList("6", "7")));
    isSubtypeOfRelationships.put("4", Collections.singleton("7"));
    isSubtypeOfRelationships.put("6", Collections.singleton("8"));

    // Expected map is:
    Map<String, Set<String>> applicationToMainApplicationMap = new HashMap<>();
    applicationToMainApplicationMap.put("2", Collections.singleton("2"));
    applicationToMainApplicationMap.put("3", Collections.singleton("3"));
    applicationToMainApplicationMap.put("4", Collections.singleton("2"));
    applicationToMainApplicationMap.put("5", Collections.singleton("3"));
    applicationToMainApplicationMap.put("6", Collections.singleton("3"));
    applicationToMainApplicationMap.put("7", new HashSet<>(Arrays.asList("2", "3")));
    applicationToMainApplicationMap.put("8", Collections.singleton("3"));

    // Final test
    assertEquals(applicationToMainApplicationMap, BrendaChebiOntology.getApplicationToMainApplicationsMap(isSubtypeOfRelationships, "1"));
  }
}
