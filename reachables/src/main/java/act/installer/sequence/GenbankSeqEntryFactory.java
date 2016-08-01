package act.installer.sequence;

import act.server.MongoDB;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;

import java.util.List;
import java.util.Map;

public class GenbankSeqEntryFactory {

  public GenbankSeqEntry createFromDNASequenceReference(AbstractSequence sequence,
                                                        Map<String, List<Qualifier>> qualifierMap, MongoDB db) {
    GenbankSeqEntry se = new GenbankSeqEntry(sequence, qualifierMap);
    se.init(db);
    return se;
  }

  public GenbankSeqEntry createFromProteinSequenceReference(AbstractSequence sequence, MongoDB db) {
    GenbankSeqEntry se = new GenbankSeqEntry(sequence);
    se.init(db);
    return se;
  }

}
