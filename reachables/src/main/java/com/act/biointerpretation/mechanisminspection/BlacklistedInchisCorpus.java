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

package com.act.biointerpretation.mechanisminspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class BlacklistedInchisCorpus {
  private static final String FILE_PATH = "blacklisted_inchis.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("blacklist_inchis")
  private List<BlacklistedInchi> blacklistedInchis;

  public List<BlacklistedInchi> getInchis() {
    return blacklistedInchis;
  }

  public void setInchis(List<BlacklistedInchi> inchis) {
    this.blacklistedInchis = inchis;
  }

  public BlacklistedInchisCorpus() {}

  public void loadCorpus() throws IOException {
    InputStream inputStream = INSTANCE_CLASS_LOADER.getResourceAsStream(FILE_PATH);
    BlacklistedInchisCorpus corpus = OBJECT_MAPPER.readValue(inputStream, BlacklistedInchisCorpus.class);
    setInchis(corpus.getInchis());
  }

  /**
   * This function renames a given inchi to the correct one if the inchi matches the busted inchi list.
   * @param inchi The inchi to compare against
   * @return The renamed inchi if it is busted. Else, return the input back.
   */
  public String renameInchiIfFoundInBlacklist(String inchi) {
    if (blacklistedInchis != null) {
      for (BlacklistedInchi blacklistedInchi : blacklistedInchis) {
        if (inchi.equals(blacklistedInchi.getWrong_inchi())) {
          return blacklistedInchi.getCorrect_inchi();
        }
      }
    }
    return inchi;
  }
}
