package act.installer.metacyc.references;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;

public class Publication extends BPElement {
  Integer year;
  String title;
  Set<String> source;
  String db, id;
  Set<String> authors;

  // an example (xml, but jsonified for readability, so please ignore the multikey) being:
  // { year: 2004,
  //   title: "A Bayesian method for identifying missing enzymes in predicted metabolic pathway databases."
  //   source: "BMC Bioinformatics 5;76"
  //   id: "15189570"
  //   db: "PubMed"
  //   author: "Green ML"
  //   author: "Karp PD"
  // }

  public Publication(BPElement basics, int year, String t, Set<String> src, String db, String id, Set<String> auth) {
    super(basics);
    this.year = year;
    this.title = t;
    this.source = src;
    this.db = db;
    this.id = id;
    this.authors = auth;
  }

  public String citation() {
    return "\""  + title + "\", " + source + ", " + year + ", " + authors;
  }

  public String dbid() {
    return db + "_" + id;
  }
}

