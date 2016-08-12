package act.installer.pubchem;

import java.util.HashMap;
import java.util.Map;

//The PubchemSynonymType enum was taken and simplified from the PubchemTTLMerger class
public enum PubchemSynonymType {
  // Names derived from the Semantic Chemistry Ontology: https://github.com/egonw/semanticchemistry
  TRIVIAL_NAME("CHEMINF_000109"),
  DEPOSITORY_NAME("CHEMINF_000339"),
  IUPAC_NAME("CHEMINF_000382"),
  DRUG_BANK_ID("CHEMINF_000406"),
  CHEBI_ID("CHEMINF_000407"),
  KEGG_ID("CHEMINF_000409"),
  CHEMBL_ID("CHEMINF_000412"),
  CAS_REGISTRY_NUMBER("CHEMINF_000446"),
  EC_NUMBER("CHEMINF_000447"),
  VALIDATED_CHEM_DB_ID("CHEMINF_000467"),
  DRUG_TRADE_NAME("CHEMINF_000561"),
  INTL_NONPROPRIETARY_NAME("CHEMINF_000562"),
  UNIQUE_INGREDIENT_ID("CHEMINF_000563"),
  LIPID_MAPS_ID("CHEMINF_000564"),
  NSC_NUMBER("CHEMINF_000565"),
  RTECS_ID("CHEMINF_000566"),
  UNKNOWN("NO_ID")
  ;

  private static final Map<String, PubchemSynonymType> CHEMINF_TO_TYPE = new HashMap<String, PubchemSynonymType>() {{
    for (PubchemSynonymType type : PubchemSynonymType.values()) {
      put(type.getCheminfId(), type);
    }
  }};

  public static PubchemSynonymType getByCheminfId(String cheminfId) {
    return CHEMINF_TO_TYPE.getOrDefault(cheminfId, UNKNOWN);
  }

  String cheminfId;

  public String getCheminfId() {
    return cheminfId;
  }

  PubchemSynonymType(String cheminfId) {
    this.cheminfId = cheminfId;
  }
}
