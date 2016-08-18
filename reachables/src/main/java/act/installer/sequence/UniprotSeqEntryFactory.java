package act.installer.sequence;

import act.server.MongoDB;
import org.w3c.dom.Document;
import java.util.Map;

public class UniprotSeqEntryFactory {

  public UniprotSeqEntry createFromDocumentReference(Document doc, MongoDB db,
                                                     Map<String, String> minimalPrefixMapping) {
    UniprotSeqEntry se = new UniprotSeqEntry(doc, minimalPrefixMapping);
    se.init(db);
    return se;
  }

}
