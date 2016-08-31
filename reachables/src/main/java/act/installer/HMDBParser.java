package act.installer;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jaxen.JaxenException;
import org.jaxen.dom.DOMXPath;

public class HMDBParser {

  /*
  HMBD XPath

  Primary name: /metabolite/name/text()
  Synonyms: /metabolite/synonyms/synonym
  IUPAC name: /metabolite/iupac_name/text()
  SMILES: /metabolite/smiles/text()
  InChI: /metabolite/inchi/text()
  InChIKey: /metabolite/inchikey/text()

  Ontology:
    Status: /metabolite/ontology/status/text()
    Origins: /metabolite/ontology/origins/origin
    Functions: /metabolite/ontology/functions/function
    Applications: /metabolite/ontology/applications/application
    Location: /metabolite/ontology/cellular_locations/cellular_location

  Experimental properties?
  Skip predicted properties
  Do we want Specdb ids?

  Fluids: /metabolite/biofluid_locations/biofluid
  Tissue: /metabolite/tissue_locations/tissue

  Pathways: /metabolite/pathways/pathway/name (just grab name for now)

  Skip concentraions for now maybe?

  Diseases: /metabolite/diseases/disease/name
  Metlin id: /metabolite/metlin_id/text()
  Pubchem CID: /metabolite/pubchem_compound_id/text()
  ChEBI id: /metabolite/chebi_id/text()
  Proteins:
    Name: /metabolite/protein_associations/protein/name/text()
    Uniprot: /metabolite/protein_associations/protein/uniprot_id/text()
    Gene name: /metabolite/protein_associations/protein/gene_name/text()
  */

  public enum HMDB_XPATH {
    // Names
    PRIMARY_NAME_TEXT("/metabolite/name/text()"),
    IUPAC_NAME_TEXT("/metabolite/iupac_name/text()"),
    SYNONYMS_NODES("/metabolite/synonyms/synonym"),
    // Structures
    INCHI_TEXT("/metabolite/inchi/text()"),
    INCHI_KEY_TEXT("/metabolite/inchikey/text()"),
    SMILES_TEXT("/metabolite/smiles/text()"),
    // Ontology
    ONTOLOGY_STATUS_TEXT("/metabolite/ontology/status/text()"),
    ONTOLOGY_ORIGINS_NODES("/metabolite/ontology/origins/origin"),
    ONTOLOGY_FUNCTIONS_NODES("/metabolite/ontology/functions/function"),
    ONTOLOGY_APPLICATIONS_NODES("/metabolite/ontology/applications/application"),
    ONTOLOGY_LOCATIONS_NODES("/metabolite/ontology/cellular_locations/cellular_location"),
    // Physiological locality
    LOCATIONS_FLUID_NODES("/metabolite/biofluid_locations/biofluid"),
    LOCATIONS_TISSUE_NODES("/metabolite/tissue_locations/tissue"),
    // Metabolic pathways
    PATHWAY_NAME_NODES("/metabolite/pathways/pathway/name"),
    // Diseases
    DISEASE_NAME_NODES("/metabolite/diseases/disease/name"),
    // External IDs
    METLIN_ID_TEXT("/metabolite/metlin_id/text()"),
    PUBCHEM_ID_TEXT("/metabolite/pubchem_compound_id/text()"),
    CHEBI_ID_TEXT("/metabolite/chebi_id/text()"),
    // Proteins
    PROTEIN_NAME_TEXT("/metabolite/protein_associations/protein/name/text()"),
    PROTEIN_UNIPROT_ID_TEXT("/metabolite/protein_associations/protein/uniprot_id/text()"),
    PROTEIN_GENE_NAME_TEXT("/metabolite/protein_associations/protein/gene_name/text()"),

    /* Features we're not extracting right now:
     * * Normal/abnormal concentrations in different fluids/tissues (too many different kinds of expression/units)
     * * Experimentally derived and predicted properties (many of the latter come from Chemaxon anyway)
     * * "specdb" ids, which represent NRM/MS2 data out there, not sure how useful this is right now
     * * Pathway details and ids, which hopefully are already captured via Metacyc
     * * Literature references, which we'd only inspect manually at present and we can always return to the source
     */
    ;

    String path;

    HMDB_XPATH(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }

    DOMXPath compile() throws JaxenException {
      return new DOMXPath(this.getPath());
    }
  }
}
