package act.installer.sequence;

import act.server.MongoDB;
import org.w3c.dom.Document;

public class UniprotSeqEntryFactory {

  public UniprotSeqEntry createFromDocumentReference(Document doc, MongoDB db) {
    UniprotSeqEntry se = new UniprotSeqEntry(doc);
    se.init(db);
    return se;
  }

}
