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

package act.installer.pubchem;

import java.util.HashMap;
import java.util.Map;

public enum MeshTermType {
  // Names derived from the MeSH XML data elements page (https://www.nlm.nih.gov/mesh/xml_data_elements.html)
  // section LexicalTags.

  // Abbreviation, embedded abbreviation and acronym are self explanatory and describe well what they tag
  // They will be ignored for web queries
  ABBREVIATION("ABB"),
  EMBEDDED_ABBREVIATION("ABX"),
  ACRONYM("ACR"),
  // Examples of EPO tagged terms: Thomsen-Friedenreich antigen", "Brompton mixture",
  // "Andrew's Liver Salt", "Evans Blue", "Schiff Bases", "Giemsa Stain"
  EPONYM("EPO"),
  // Lab number: ignore for web queries
  LAB_NUMBER("LAB"),
  // Examples of NAM tagged terms: "Benin", "India", "Rome", "Taiwan", "United Nations", "Saturn"
  // Ignore by all means
  PROPER_NAME("NAM"),
  // NON tags the non-classified names, including most of the good ones
  NONE("NON"),
  // Trade names are tagged with TRD and seem to be of good quality.
  TRADE_NAME("TRD"),
  ;

  private static final Map<String, MeshTermType> LEXICAL_TAG_TO_TYPE = new HashMap<String, MeshTermType>() {{
    for (MeshTermType type : MeshTermType.values()) {
      put(type.getLexicalTag(), type);
    }
  }};

  public static MeshTermType getByLexicalTag(String lexicalTag) {
    return LEXICAL_TAG_TO_TYPE.getOrDefault(lexicalTag, NONE);
  }

  String lexicalTag;

  public String getLexicalTag() {
    return lexicalTag;
  }

  MeshTermType(String lexicalTag) {
    this.lexicalTag = lexicalTag;
  }

  @Override
  public String toString() {
    return this.name();
  }
}
