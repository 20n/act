/*package act.installer.reachablesexplorer

import act.shared.Chemical
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.BasicDBObject
import java.util.ArrayList
import java.util.List
import java.util.Map

@JsonInclude(JsonInclude.Include.ALWAYS) object Reachable {
  @JsonIgnore private[reachablesexplorer] val inchiFieldName: String = "InChI"
}

@JsonInclude(JsonInclude.Include.ALWAYS) class Reachable {
  @JsonProperty("_id") private var id: Long = 0L
  private var pageName: String = null
  private var pathwayVisualization: String = null
  private var isNative: Boolean = false
  @JsonProperty("InChI") private var inchi: String = null
  private var smiles: String = null
  private var inchiKey: String = null
  private var structureFilename: String = null
  private var names: util.List[String] = null
  private var wordCloudFilename: String = null
  private var precursorData: PrecursorData = null
  private var xref: util.Map[Chemical.REFS, BasicDBObject] = null
  private var synonyms: SynonymData = null
  private var patentSummaries: util.List[PatentSummary] = null

  def this(id: Long, pageName: String, inchi: String, smiles: String, inchiKey: String, names: util.List[String], precursors: PrecursorData, synonyms: SynonymData, isNative: Boolean, structureFilename: String, wordCloudFilename: String, pathwayVisualization: String, xref: util.Map[Chemical.REFS, BasicDBObject], patentSummaries: util.List[PatentSummary]) {
    this()
    this.id = id
    this.pageName = pageName
    this.inchi = inchi
    this.smiles = smiles
    this.inchiKey = inchiKey
    this.names = names
    this.precursorData = precursors
    this.synonyms = synonyms
    this.isNative = isNative
    this.structureFilename = structureFilename
    this.wordCloudFilename = wordCloudFilename
    this.pathwayVisualization = pathwayVisualization
    this.xref = xref
    this.patentSummaries = patentSummaries
  }

  def this(id: Long, pageName: String, inchi: String, smiles: String, inchiKey: String, names: util.List[String], synonymData: SynonymData, structureFilename: String, wordCloudFilename: String, xref: util.Map[Chemical.REFS, BasicDBObject]) {
    this(id, pageName, inchi, smiles, inchiKey, names, new PrecursorData, synonymData, null, structureFilename, wordCloudFilename, null, xref, null)
  }

  def getPageName: String = {
    return pageName
  }

  def setPageName(pageName: String) {
    this.pageName = pageName
  }

  def getPathwayVisualization: String = {
    return pathwayVisualization
  }

  def setPathwayVisualization(pathwayVisualization: String) {
    this.pathwayVisualization = pathwayVisualization
  }

  def getNative: Boolean = {
    return isNative
  }

  def setNative(aNative: Boolean) {
    isNative = aNative
  }

  @JsonIgnore def getInchi: String = {
    return inchi
  }

  @JsonIgnore def setInchi(inchi: String) {
    this.inchi = inchi
  }

  def getSmiles: String = {
    return smiles
  }

  def setSmiles(smiles: String) {
    this.smiles = smiles
  }

  def getInchiKey: String = {
    return inchiKey
  }

  def setInchiKey(inchiKey: String) {
    this.inchiKey = inchiKey
  }

  def getStructureFilename: String = {
    return structureFilename
  }

  def setStructureFilename(structureFilename: String) {
    this.structureFilename = structureFilename
  }

  def getNames: util.List[String] = {
    return names
  }

  def setNames(names: util.List[String]) {
    this.names = names
  }

  def getWordCloudFilename: String = {
    return wordCloudFilename
  }

  def setWordCloudFilename(wordCloudFilename: String) {
    this.wordCloudFilename = wordCloudFilename
  }

  def getPrecursorData: PrecursorData = {
    return precursorData
  }

  def setPrecursorData(precursorData: PrecursorData) {
    this.precursorData = precursorData
  }

  def getXref: util.Map[Chemical.REFS, BasicDBObject] = {
    return xref
  }

  def setXref(xref: util.Map[Chemical.REFS, BasicDBObject]) {
    this.xref = xref
  }

  def getSynonyms: SynonymData = {
    return synonyms
  }

  def setSynonyms(synonyms: SynonymData) {
    this.synonyms = synonyms
  }

  def getPatentSummaries: util.List[PatentSummary] = {
    return this.patentSummaries
  }

  def setPatentSummaries(patentSummaries: util.List[PatentSummary]) {
    this.patentSummaries = patentSummaries
  }

  def addPatentSummaries(patentSummaries: util.List[PatentSummary]) {
    if (this.patentSummaries == null) {
      this.patentSummaries = new util.ArrayList[PatentSummary]
    }
    this.patentSummaries.addAll(patentSummaries)
  }
}**/