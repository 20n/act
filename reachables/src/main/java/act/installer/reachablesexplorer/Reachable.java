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


import act.shared.Chemical;
import act.shared.helpers.MongoDBToJSON;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Reachable {
  @JsonIgnore
  static final String ID_FIELD_NAME = "_id";
  @JsonIgnore
  static final String INCHI_FIELD_NAME = "InChI";


  @JsonProperty("_id")
  private Long id;
  private String pageName;
  private String pathwayVisualization;
  private Boolean isNative;
  @JsonProperty("InChI")
  private String inchi;
  private String smiles;
  private String inchiKey;
  private String structureFilename;
  private List<String> names;
  private String wordCloudFilename;
  private PrecursorData precursorData;
  private Map<Chemical.REFS, BasicDBObject> xref;
  private SynonymData synonyms;
  private List<PatentSummary> patentSummaries;
  private PhysiochemicalProperties physiochemicalProperties;

  public Reachable() {}

  public Reachable(Long id,
                   String pageName,
                   String inchi,
                   String smiles,
                   String inchiKey,
                   List<String> names,
                   PrecursorData precursors,
                   SynonymData synonyms,
                   Boolean isNative,
                   String structureFilename,
                   String wordCloudFilename,
                   String pathwayVisualization,
                   Map<Chemical.REFS, BasicDBObject> xref,
                   List<PatentSummary> patentSummaries,
                   PhysiochemicalProperties physiochemicalProperties) {
    this.id = id;
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchiKey = inchiKey;
    this.names = names;
    this.precursorData = precursors;
    this.synonyms = synonyms;
    this.isNative = isNative;
    this.structureFilename = structureFilename;
    this.wordCloudFilename = wordCloudFilename;
    this.pathwayVisualization = pathwayVisualization;
    this.xref = xref;
    this.patentSummaries = patentSummaries;
    this.physiochemicalProperties = physiochemicalProperties;
  }

  public Reachable(
      Long id, String pageName, String inchi, String smiles, String inchiKey, List<String> names,
      SynonymData synonymData, String structureFilename, String wordCloudFilename, Map<Chemical.REFS,
      BasicDBObject> xref, PhysiochemicalProperties physiochemicalProperties) {
    // TODO: these arguments are out of hand, and this could use a Builder.
    this(id, pageName, inchi, smiles, inchiKey, names, new PrecursorData(), synonymData, null,
        structureFilename, wordCloudFilename, null, xref, null, physiochemicalProperties);
  }

  public Long getId() {
    return id;
  }

  public String getPageName() {
    return pageName;
  }

  public void setPageName(String pageName) {
    this.pageName = pageName;
  }

  public String getPathwayVisualization() {
    return pathwayVisualization;
  }

  public void setPathwayVisualization(String pathwayVisualization) {
    this.pathwayVisualization = pathwayVisualization;
  }

  public Boolean getNative() {
    return isNative;
  }

  public void setNative(Boolean aNative) {
    isNative = aNative;
  }

  @JsonIgnore
  public String getInchi() {
    return inchi;
  }

  @JsonIgnore
  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public String getSmiles() {
    return smiles;
  }

  public void setSmiles(String smiles) {
    this.smiles = smiles;
  }

  public String getInchiKey() {
    return inchiKey;
  }

  public void setInchiKey(String inchiKey) {
    this.inchiKey = inchiKey;
  }

  public String getStructureFilename() {
    return structureFilename;
  }

  public void setStructureFilename(String structureFilename) {
    this.structureFilename = structureFilename;
  }

  public List<String> getNames() {
    return names;
  }

  public void setNames(List<String> names) {
    this.names = names;
  }

  public String getWordCloudFilename() {
    return wordCloudFilename;
  }

  public void setWordCloudFilename(String wordCloudFilename) {
    this.wordCloudFilename = wordCloudFilename;
  }

  public PrecursorData getPrecursorData() {
    return precursorData;
  }

  public void setPrecursorData(PrecursorData precursorData) {
    this.precursorData = precursorData;
  }

  public Map<Chemical.REFS, BasicDBObject> getXref() {
    return xref;
  }

  public void setXref(Map<Chemical.REFS, BasicDBObject> xref) {
    this.xref = xref;
  }

  public SynonymData getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(SynonymData synonyms) {
    this.synonyms = synonyms;
  }

  public List<PatentSummary> getPatentSummaries() {
    return this.patentSummaries;
  }

  public void setPatentSummaries(List<PatentSummary> patentSummaries) {
    this.patentSummaries = patentSummaries;
  }

  public PhysiochemicalProperties getPhysiochemicalProperties() {
    return physiochemicalProperties;
  }

  public void setPhysiochemicalProperties(PhysiochemicalProperties physiochemicalProperties) {
    this.physiochemicalProperties = physiochemicalProperties;
  }

  public void addPatentSummaries(List<PatentSummary> patentSummaries) {
    if (this.patentSummaries == null) {
      this.patentSummaries = new ArrayList<>();
    }
    this.patentSummaries.addAll(patentSummaries);
  }

}
