package act.installer.metacyc;

import act.installer.metacyc.entities.*;
import act.installer.metacyc.processes.*;
import act.installer.metacyc.annotations.*;
import act.installer.metacyc.references.*;

import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class OrganismComposition {
  // Entities
  HashMap<Resource, Protein> proteins;
  HashMap<Resource, RNA> rnas;
  HashMap<Resource, ProteinRNARef> proteinRnaRefs;
  HashMap<Resource, SmallMolecule> smallMols;
  HashMap<Resource, SmallMoleculeRef> smallMolRefs;
  HashMap<Resource, Complex> complexes;
  HashMap<Resource, ChemicalStructure> chemstructs;

  // Processes
  HashMap<Resource, Pathway> pathways;
  HashMap<Resource, BiochemicalPathwayStep> pathSteps;
  HashMap<Resource, Catalysis> catalyses;
  HashMap<Resource, Modulation> modulations;
  HashMap<Resource, Conversion> rxns;
  HashMap<Resource, Conversion> rxns_wtransport;
  HashMap<Resource, Conversion> transports;

  // Annotations
  HashMap<Resource, Term> terms; // CellularLocationVocabulary,
                                // EvidenceCodeVocabulary,
                                // RelationshipTypeVocabulary
  HashMap<Resource, Stoichiometry> stoichiometries;
  HashMap<Resource, DeltaG> deltaGs;
  HashMap<Resource, BioSource> bioSources;

  // References
  HashMap<Resource, Evidence> evidences;
  HashMap<Resource, Provenance> provenances;
  HashMap<Resource, Publication> publications; // PublicationXref
  HashMap<Resource, Relationship> relationships; // RelationshipXref
  HashMap<Resource, Unification> unifications; // UnificationXref

  // Uncategorized
  HashMap<Resource, BPElement> uncategorized;

  // Map with the union of all
  HashMap<Resource, BPElement> everybody;
  
  public OrganismComposition() {
    this.proteins = new HashMap<Resource, Protein>();
    this.rnas = new HashMap<Resource, RNA>();
    this.proteinRnaRefs = new HashMap<Resource, ProteinRNARef>();
    this.smallMols = new HashMap<Resource, SmallMolecule>();
    this.smallMolRefs = new HashMap<Resource, SmallMoleculeRef>();
    this.complexes = new HashMap<Resource, Complex>();
    this.chemstructs = new HashMap<Resource, ChemicalStructure>();

    this.pathways = new HashMap<Resource, Pathway>();
    this.pathSteps = new HashMap<Resource, BiochemicalPathwayStep>();
    this.catalyses = new HashMap<Resource, Catalysis>();
    this.modulations = new HashMap<Resource, Modulation>();
    this.rxns = new HashMap<Resource, Conversion>();
    this.rxns_wtransport = new HashMap<Resource, Conversion>();
    this.transports = new HashMap<Resource, Conversion>();

    this.terms = new HashMap<Resource, Term>();
    this.stoichiometries = new HashMap<Resource, Stoichiometry>();
    this.deltaGs = new HashMap<Resource, DeltaG>();
    this.bioSources = new HashMap<Resource, BioSource>();

    this.evidences = new HashMap<Resource, Evidence>();
    this.provenances = new HashMap<Resource, Provenance>();
    this.publications = new HashMap<Resource, Publication>();
    this.relationships = new HashMap<Resource, Relationship>();
    this.unifications = new HashMap<Resource, Unification>();

    this.uncategorized = new HashMap<Resource, BPElement>();
    this.everybody = new HashMap<Resource, BPElement>();
  }

  public void add(Resource id, Object res) {
    if (this.everybody.containsKey(id)) {
      System.err.println("Duplicate id being installed. Abort. Will cause unrelated conflation. Abort.");
      System.err.println(id + " -> " + res);
      System.exit(-1);
    }

    // add to the whole container, for easy resolution
    this.everybody.put(id, (BPElement)res);

    // add to the fine grained maps by types
    if (res instanceof Protein)                 
      this.proteins.put(id, (Protein)res);
    else if (res instanceof RNA)                
      this.rnas.put(id, (RNA)res);
    else if (res instanceof ProteinRNARef)      
      this.proteinRnaRefs.put(id, (ProteinRNARef)res);
    else if (res instanceof SmallMolecule)      
      this.smallMols.put(id, (SmallMolecule)res);
    else if (res instanceof SmallMoleculeRef)   
      this.smallMolRefs.put(id, (SmallMoleculeRef)res);
    else if (res instanceof Complex)            
      this.complexes.put(id, (Complex)res);
    else if (res instanceof ChemicalStructure)  
      this.chemstructs.put(id, (ChemicalStructure)res);

    else if (res instanceof Pathway)                
      this.pathways.put(id, (Pathway)res);
    else if (res instanceof BiochemicalPathwayStep) 
      this.pathSteps.put(id, (BiochemicalPathwayStep)res);
    else if (res instanceof Catalysis)              
      this.catalyses.put(id, (Catalysis)res);
    else if (res instanceof Modulation)              
      this.modulations.put(id, (Modulation)res);
    else if (res instanceof Conversion) {
      Conversion c = (Conversion)res;
      if (c.getTyp() == Conversion.TYPE.BIOCHEMICAL_RXN) 
        this.rxns.put(id, (Conversion)res);
      else if (c.getTyp() == Conversion.TYPE.TRANSPORT_W_BIOCHEMICAL_RXN) 
        this.rxns_wtransport.put(id, (Conversion)res);
      else if (c.getTyp() == Conversion.TYPE.TRANSPORT)              
        this.transports.put(id, (Conversion)res);
    }

    else if (res instanceof Term)              
      this.terms.put(id, (Term)res);
    else if (res instanceof Stoichiometry)      
      this.stoichiometries.put(id, (Stoichiometry)res);
    else if (res instanceof DeltaG)             
      this.deltaGs.put(id, (DeltaG)res);
    else if (res instanceof BioSource)          
      this.bioSources.put(id, (BioSource)res);

    else if (res instanceof Evidence)           
      this.evidences.put(id, (Evidence)res);
    else if (res instanceof Provenance)         
      this.provenances.put(id, (Provenance)res);
    else if (res instanceof Publication)        
      this.publications.put(id, (Publication)res);
    else if (res instanceof Relationship)       
      this.relationships.put(id, (Relationship)res);
    else if (res instanceof Unification)        
      this.unifications.put(id, (Unification)res);

    else if (res instanceof BPElement)
      this.uncategorized.put(id, (BPElement)res);

    else {
      System.err.println("Attempt to add invalid obj to OrganismComposition: " 
          + res.getClass());
      System.exit(-1);
    }

  }

  public Set<BPElement> resolve(Set<Resource> ids) {
    Set<BPElement> out = new HashSet<BPElement>();
    for (Resource id : ids) {
      BPElement bpe = resolve(id);
      out.add(bpe);
    }
    return out;
  }

  public BPElement resolve(Resource id) {
    return this.everybody.get(id);
  }

  public HashMap getMap(Conversion.TYPE t) {
    switch (t) {
      case BIOCHEMICAL_RXN: return this.rxns;
      case TRANSPORT_W_BIOCHEMICAL_RXN: return this.rxns_wtransport;
      case TRANSPORT: return this.transports;
    }
    return null;
  }

  public HashMap getMap(Class t) {
    if (t == Catalysis.class) return this.catalyses;
    if (t == Protein.class) return this.proteins;
    if (t == RNA.class) return this.rnas;
    if (t == ProteinRNARef.class) return this.proteinRnaRefs;
    if (t == SmallMolecule.class) return this.smallMols;
    if (t == SmallMoleculeRef.class) return this.smallMolRefs;
    if (t == Complex.class) return this.complexes;
    if (t == ChemicalStructure.class) return this.chemstructs;

    if (t == Pathway.class) return this.pathways;
    if (t == BiochemicalPathwayStep.class) return this.pathSteps;
    if (t == Catalysis.class) return this.catalyses;
    if (t == Modulation.class) return this.modulations;

    if (t == Conversion.class) return null;
    if (t == Conversion.class) return null;
    if (t == Conversion.class) return null;

    if (t == Term.class) return this.terms;
    if (t == Stoichiometry.class) return this.stoichiometries;
    if (t == DeltaG.class) return this.deltaGs;
    if (t == BioSource.class) return this.bioSources;

    if (t == Evidence.class) return this.evidences;
    if (t == Provenance.class) return this.provenances;
    if (t == Publication.class) return this.publications;
    if (t == Relationship.class) return this.relationships;
    if (t == Unification.class) return this.unifications;

    return null;
  }

  public void test_szes_ecol679205_hmpcyc() {
    // for the test file ecol679205_hmpcyc/biopax-level3.owl
    // the sizes of these hashmaps have to be the following
    assert proteins.size() == 1380;
    assert rnas.size() == 50;
    assert proteinRnaRefs.size() == 1371 + 50; // 1371 proteinref, 50 rnaref
    assert smallMols.size() == 1497;
    assert smallMolRefs.size() == 1375;
    assert complexes.size() == 26;
    assert chemstructs.size() == 1304;

    assert pathways.size() == 374;
    assert pathSteps.size() == 1732;
    assert catalyses.size() == 2264;
    assert modulations.size() == 0;
    assert rxns.size() == 1394;
    assert rxns_wtransport.size() == 92;
    assert transports.size() == 101;

    assert terms.size() == 6; // cellular=3 + evidence=1 + reln=2
    assert stoichiometries.size() == 1714;
    assert deltaGs.size() == 20;
    assert bioSources.size() == 1;

    assert evidences.size() == 2;
    assert provenances.size() == 1;
    assert publications.size() == 4;
    assert relationships.size() == 2806; 
    assert unifications.size() == 12424;

    System.out.println("Consistency check test_szes_ecol679205_hmpcyc passed.");
  }
}
