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

package act.installer.reachablesexplorer;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemSynonymType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class SynonymData {
  @JsonProperty("pubchem")
  private Map<PubchemSynonymType, Set<String>> pubchemSynonyms;
  @JsonProperty("mesh")
  private Map<MeshTermType, Set<String>> meshHeadings;

  @JsonCreator
  public SynonymData(@JsonProperty("pubchem") Map<PubchemSynonymType, Set<String>> pubchemSynonyms,
                  @JsonProperty("mesh") Map<MeshTermType, Set<String>> meshHeadings) {
    this.pubchemSynonyms = pubchemSynonyms;
    this.meshHeadings = meshHeadings;
  }

  public Map<PubchemSynonymType, Set<String>> getPubchemSynonyms() {
    return Collections.unmodifiableMap(this.pubchemSynonyms); // TODO: is unmodifiable worth the overhead?
  }

  public Set<String> getPubchemSynonymsByType(PubchemSynonymType type) {
    return this.pubchemSynonyms.containsKey(type) ?
        Collections.unmodifiableSet(this.pubchemSynonyms.get(type)) :
        Collections.emptySet();
  }

  public Map<MeshTermType, Set<String>> getMeshHeadings() {
    return Collections.unmodifiableMap(this.meshHeadings);
  }

  public Set<String> getMeshHeadingsByType(MeshTermType type) {
    return this.meshHeadings.containsKey(type) ?
        Collections.unmodifiableSet(this.meshHeadings.get(type)) :
        Collections.emptySet();
  }
}
