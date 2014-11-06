package act.shared.sar;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import act.shared.Seq;
import act.shared.Chemical;
import act.server.SQLInterface.MongoDB;
import act.server.Molecules.MCS;
import act.server.Molecules.DotNotation;
import act.server.Molecules.SMILES;
import act.server.Molecules.MolGraph;
import act.shared.AAMFailException;
import act.shared.MalFormedReactionException;
import act.shared.CannotProcessChemicalStructureException;
import act.server.Logger;
import com.ggasoftware.indigo.Indigo;

public class SARInfer {

  private MongoDB db;
  private static final int _debug_level = 1; // 0 = no log; 1 = only main stats; 2 = all

  public SARInfer(MongoDB db) {
    this.db = db;

    Logger.setMaxImpToShow(3); // only informational
  }

  public void infer() {
    System.out.println("[INFER_SAR] Inferring SAR for all sequences");
    List<Long> seqids = db.getAllSeqUUIDs();
    double done = 0; double total = seqids.size(); 
    for (Long seqid : seqids) {
      Seq s = db.getSeqFromID(seqid);
      try {
        SAR sar = infer_sar(s);
        s.setSAR(sar);
        db.updateSARConstraint(s);
      } catch (Exception e) {
        System.err.println("Failed MCS: Seq ID:" + s.getUUID());
      }
      System.out.format("[MAP_SEQ] Done: %.0f%%\n", (100*done++/total));
    }
    System.out.println();
  }

  public void infer(List<String> accessions) {
    System.out.format("[INFER_SAR] Inferring SAR for %d accessions\n", accessions.size());
    double done = 0; double total = accessions.size(); 
    for (String acc : accessions) {
      Seq s = db.getSeqFromAccession(acc);
      try {
        SAR sar = infer_sar(s);
        s.setSAR(sar);
        db.updateSARConstraint(s);
      } catch (Exception e) {
        System.err.format("Failed MCS: Accession: %s, Seq ID: %d\n", acc, s.getUUID());
      }
      System.out.format("[MAP_SEQ] Done: %.0f%%\n", (100*done++/total));
    }
    System.out.println();
  }

  private SAR infer_sar(Seq seq) 
    throws AAMFailException, 
            MalFormedReactionException, 
            CannotProcessChemicalStructureException {

    HashMap<Chemical, String> substrate_diversity = get_diversity_substrates(seq);
    List<List<String>> to_mcs = list_of_substrates(substrate_diversity);

    // This is a recursive algorithm that incrementally takes 
    // the MCS of \forall i (smiles[0], smiles[i])
    // until the set of smiles goes down to a singleton list.
    //
    // We can check this algorithm by manually computing
    // MCS using the web tool: http://chemmine.ucr.edu/similarity/
    MolGraph mcs = new MCS(to_mcs).getMCS();

    String smiles = SMILES.FromGraphWithoutUnknownAtoms(new Indigo(), mcs);

    SAR sar = new SAR();
    sar.addConstraint(SAR.ConstraintPresent.should_have, 
                      SAR.ConstraintContent.substructure, 
                      SAR.ConstraintRequire.soft, 
                      smiles);

    System.out.format("[INFER_SAR] Accession: %s MCS: %s\n\n", seq.get_uniprot_accession(), sar);
    System.out.format("[INFER_SAR] TODO: compute hard, should_not_have constraints from -ve observations\n");
    System.out.format("[INFER_SAR] TODO: add hard, should_have constraints from ero precondition\n");

    return sar;
  }

  private List<List<String>> list_of_substrates(HashMap<Chemical, String> all_chemicals) {
    List<List<String>> to_mcs = new ArrayList<List<String>>();
    for (String sm : all_chemicals.values()) {
      List<String> singleton = new ArrayList<String>();
      singleton.add(sm);
      to_mcs.add(singleton);
    }
    System.out.println("[INFER_SAR] MCS computation over: " + to_mcs);
  }

  private HashMap<Chemical, String> get_diversity_substrates(Seq seq) throws CannotProcessChemicalStructureException {
    HashMap<Chemical, String> substrate_diversity = new HashMap<Chemical, String>();
    for (Long s : seq.getCatalysisSubstratesDiverse()) {
      Chemical c = db.getChemicalFromChemicalUUID(s);
      String smiles = c.getSmiles();
      if (smiles == null) {
        throw new CannotProcessChemicalStructureException("Chemical: " + c.getUuid());
      }
      substrate_diversity.put(c, c.getSmiles());
    }
    return substrate_diversity;
  }


}

