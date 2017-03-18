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

package com.act.biointerpretation.cofactorremoval;

import act.shared.Chemical;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class finds for cofactor-like components within a fake inchi.
 * Created by jca20n on 11/12/1.
 */
public class FakeCofactorFinder {
  private static final Logger LOGGER = LogManager.getFormatterLogger(FakeCofactorFinder.class);
  private Map<String, String> fakeCofactorToRealCofactorName;

  public FakeCofactorFinder() {
    try {
      FakeCofactorCorpus corpus = new FakeCofactorCorpus();
      corpus.loadCorpus();
      fakeCofactorToRealCofactorName = corpus.getFakeCofactorNameToRealCofactorName();
    } catch (IOException e) {
      LOGGER.error(String.format("Error hydrating the fake cofactor corpus. Error: %s", e.getMessage()));
      fakeCofactorToRealCofactorName = new LinkedHashMap<>();
    }
  }

  /**
   * This function scans a fake inchi chemical and detects if it has a cofactor in it.
   * @param chemical - The chemical being analyzed
   * @return - The cofactor present within the chemical.
   */
  public String scanAndReturnCofactorNameIfItExists(Chemical chemical) {
    Set<String> names = new HashSet<>();
    names.addAll(chemical.getBrendaNames());
    names.addAll(chemical.getSynonyms());
    names.addAll(chemical.getPubchemNames().keySet());
    names.addAll(chemical.getPubchemNameTypes());
    names.addAll(chemical.getKeywords());
    names.addAll(chemical.getCaseInsensitiveKeywords());

    JSONObject metacycData = chemical.getRef(Chemical.REFS.METACYC);

    if (metacycData != null) {
      JSONArray meta = metacycData.getJSONArray("meta");
      JSONObject firstMetaObject = meta.getJSONObject(0);
      JSONArray metaNames = firstMetaObject.getJSONArray("names");
      for (int i = 0; i < metaNames.length(); i++) {
        String chemicalName = metaNames.getString(i);
        names.add(chemicalName);
      }
    }

    LOGGER.debug(String.format("The size of fakeCofactorToRealCofactorName is %d and the size of name is %d",
        fakeCofactorToRealCofactorName.size(), names.size()));

    for (String name : names) {
      if (fakeCofactorToRealCofactorName.containsKey(name)) {
        return fakeCofactorToRealCofactorName.get(name);
      }
    }

    return null;
  }
}
