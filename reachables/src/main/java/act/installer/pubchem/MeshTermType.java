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
