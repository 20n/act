package act.installer.sequence;

import act.installer.brenda.BrendaRxnEntry;
import act.installer.brenda.BrendaSupportingEntries;
import act.shared.Reaction;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: make the SequenceEntry methods public so that this doesn't have to live in the installer.sequence package.
public class BrendaEntry extends SequenceEntry {
  // Note: this has been mostly copied from MetacycEntry.
  JSONObject data;

  public static SequenceEntry initFromBrendaEntry(
      long rxnId, Reaction rxn, BrendaRxnEntry brendaRxnEntry, BrendaSupportingEntries.Sequence brendaSequence,
      Long orgId) {
    return new BrendaEntry(
        brendaSequence.getSequence(),
        orgId,
        brendaSequence.getEntryName(),
        Collections.emptySet(),
        rxnId,
        rxn,
        brendaSequence.getBrendaId(),
        brendaSequence.getFirstAccessionCode()
    );
  }

  public BrendaEntry(String sequence, Long orgId, String standardName, Set<String> comments,
                     long rxnid, Reaction rxn, Integer brendaId, String firstAccessionCode) {
    this.sequence = sequence;
    this.org_id = orgId;
    this.refs = new ArrayList<>();
    this.ec = rxn.getECNum();

    // inits this.catalyzed_{rxns, substrates, products}
    extract_catalyzed_reactions(rxnid, rxn);

    // new Seq(..) looks at the metadata in this.data for SwissProt fields:
    // this.data { "name" : gene_name_eg_Adh1 }
    // this.data { "proteinExistence": { "type" : "evidence at transcript level" });
    // this.data { "comment": [ { "type": "catalytic activity", "text": uniprot_activity_annotation } ] }
    // this.data { "accession" : ["Q23412", "P87D78"] }
    // we manually add these fields so that we have consistent data

    this.data = new JSONObject();
    this.data.put("name", standardName);
    this.data.put("proteinExistence", new JSONObject());
    this.data.put("comment",
        new JSONArray(new JSONObject[] { new JSONObject().put("type", "brenda_id").put("text", brendaId) }));
    this.data.put("accession",
        new JSONObject().put("uniprot", new JSONArray(Collections.singletonList(firstAccessionCode))));

    // extract_metadata processes this.data, so do that only after updating
    // this.data with the proxy fields from above.
    this.metadata = extract_metadata();
  }

  private void extract_catalyzed_reactions(long rxnid, Reaction rxn) {
    this.sar = new SAR();
    this.catalyzed_rxns = new HashSet<Long>();
    this.catalyzed_rxns_to_substrates = new HashMap<Long, Set<Long>>();
    this.catalyzed_rxns_to_products = new HashMap<Long, Set<Long>>();
    this.catalyzed_substrates_uniform = new HashSet<Long>();
    this.catalyzed_substrates_diverse = new HashSet<Long>();
    this.catalyzed_products_uniform = new HashSet<Long>();
    this.catalyzed_products_diverse = new HashSet<Long>();

    Set<Long> rxn2substrates = new HashSet(Arrays.asList(rxn.getSubstrates()));
    Set<Long> rxn2products = new HashSet(Arrays.asList(rxn.getProducts()));

    this.catalyzed_rxns.add(rxnid);
    this.catalyzed_rxns_to_substrates.put(rxnid, rxn2substrates);
    this.catalyzed_rxns_to_products.put(rxnid, rxn2products);
    // we add all to the diverse set...
    this.catalyzed_substrates_diverse.addAll(rxn2substrates);
    this.catalyzed_products_diverse.addAll(rxn2products);
    // we do not add anything to the uniform set...

    // TODO... later, move the cofactors to the uniform set,
    //          only leave the non-cofactors in the diverse set
  }

  private DBObject extract_metadata() {
    // cannot directly return this.data coz in Seq.java
    // we expect certain specific JSON format fields
    return MongoDBToJSON.conv(this.data);
  }

  DBObject metadata;
  List<JSONObject> refs;
  String sequence;
  Long org_id;
  String ec;
  Set<Long> catalyzed_rxns;
  Set<Long> catalyzed_substrates_diverse, catalyzed_substrates_uniform;
  Set<Long> catalyzed_products_diverse, catalyzed_products_uniform;
  HashMap<Long, Set<Long>> catalyzed_rxns_to_substrates, catalyzed_rxns_to_products;
  SAR sar;


  @Override
  Long getOrgId() {
    return this.org_id;
  }

  @Override
  String getEc() {
    return this.ec;
  }

  @Override
  String getSeq() {
    return this.sequence;
  }

  @Override
  List<JSONObject> getRefs() {
    return this.refs;
  }

  @Override
  Set<Long> getCatalyzedRxns() {
    return this.catalyzed_rxns;
  }

  @Override
  HashMap<Long, Set<Long>> getCatalyzedRxnsToSubstrates() {
    return this.catalyzed_rxns_to_substrates;
  }

  @Override
  HashMap<Long, Set<Long>> getCatalyzedRxnsToProducts() {
    return this.catalyzed_rxns_to_products;
  }

  @Override
  Set<Long> getCatalyzedSubstratesUniform() {
    return this.catalyzed_substrates_uniform;
  }

  @Override
  Set<Long> getCatalyzedSubstratesDiverse() {
    return this.catalyzed_substrates_diverse;
  }

  @Override
  Set<Long> getCatalyzedProductsUniform() {
    return this.catalyzed_products_uniform;
  }

  @Override
  Set<Long> getCatalyzedProductsDiverse() {
    return this.catalyzed_products_diverse;
  }

  @Override
  SAR getSar() {
    return this.sar;
  }

  @Override
  DBObject getMetadata() {
    return this.metadata;
  }
}
