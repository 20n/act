package act.installer.metacyc;

import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.BioPAXElement;

// Entities
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.ProteinReference;
import org.biopax.paxtools.model.level3.RnaReference;
import org.biopax.paxtools.model.level3.SequenceEntityReference;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.SmallMoleculeReference;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.ChemicalStructure;

// Processes
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.BiochemicalPathwayStep;
import org.biopax.paxtools.model.level3.Catalysis;  // a type of process that appears as a step
import org.biopax.paxtools.model.level3.Modulation; // another process that appears as a step
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.biopax.paxtools.model.level3.TransportWithBiochemicalReaction;
import org.biopax.paxtools.model.level3.Transport;
import org.biopax.paxtools.model.level3.Conversion;

// Annotations
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.EvidenceCodeVocabulary;
import org.biopax.paxtools.model.level3.RelationshipTypeVocabulary;
import org.biopax.paxtools.model.level3.ControlledVocabulary;
import org.biopax.paxtools.model.level3.Stoichiometry;
import org.biopax.paxtools.model.level3.DeltaG;
import org.biopax.paxtools.model.level3.BioSource;

// References
import org.biopax.paxtools.model.level3.Evidence;
import org.biopax.paxtools.model.level3.Provenance;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.UnificationXref;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.controller.Visitor;
import org.biopax.paxtools.controller.Traverser;
import org.biopax.paxtools.controller.PropertyEditor;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.level3.Xref;
import org.biopax.paxtools.model.level3.Interaction;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.StructureFormatType;

// enums:
import org.biopax.paxtools.model.level3.StepDirection;
import org.biopax.paxtools.model.level3.CatalysisDirectionType;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.ControlType;

// things that go into BPElement
import org.biopax.paxtools.model.level3.Named;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Level3Element;
import org.biopax.paxtools.model.level3.Observable;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;

public class BioPaxFile {
  static final boolean quiet = true;
  OrganismComposition organism;

  public BioPaxFile(OrganismComposition o) {
    this.organism = o;
  }

  public void initFrom(FileInputStream is) {
    BioPAXIOHandler handler = new SimpleIOHandler();
    Model model = handler.convertFromOWL(is);

    Set<BioPAXElement> objects = model.getObjects();

    populateOrganismModel(objects);
  }

  private void populateOrganismModel(Set<BioPAXElement> all) {
    for (BioPAXElement e : all) {
      BPElement basic = getBasicData(e);
      if (!quiet) System.out.println("/--- ID: " + e.getRDFId().substring("http://biocyc.org/biopax/biopax-level3".length()));

      // Entities
      if (e instanceof Protein) addProtein(basic, (Protein)e);
      else if (e instanceof Rna) addRna(basic, (Rna)e);
      else if (e instanceof ProteinReference) addProteinRnaReference(basic, (ProteinReference)e);
      else if (e instanceof RnaReference) addProteinRnaReference(basic, (RnaReference)e);
      else if (e instanceof SmallMolecule) addSmallMolecule(basic, (SmallMolecule)e);
      else if (e instanceof SmallMoleculeReference) addSmallMoleculeReference(basic, (SmallMoleculeReference)e);
      else if (e instanceof Complex) addComplex(basic, (Complex)e);
      else if (e instanceof ChemicalStructure) addChemicalStructure(basic, (ChemicalStructure)e);
      // Processes
      else if (e instanceof Pathway) addPathway(basic, (Pathway)e);
      else if (e instanceof BiochemicalPathwayStep) addBiochemicalPathwayStep(basic, (BiochemicalPathwayStep)e);
      else if (e instanceof Catalysis) addCatalysis(basic, (Catalysis)e);
      else if (e instanceof Modulation) addModulation(basic, (Modulation)e);
      else if (e instanceof BiochemicalReaction) addConversion(basic, (BiochemicalReaction)e);
      else if (e instanceof TransportWithBiochemicalReaction) addConversion(basic, (TransportWithBiochemicalReaction)e);
      else if (e instanceof Transport) addConversion(basic, (Transport)e);
      // Annotations
      else if (e instanceof CellularLocationVocabulary) addTerm(basic, (CellularLocationVocabulary)e);
      else if (e instanceof EvidenceCodeVocabulary) addTerm(basic, (EvidenceCodeVocabulary)e);
      else if (e instanceof RelationshipTypeVocabulary) addTerm(basic, (RelationshipTypeVocabulary)e);
      else if (e instanceof Stoichiometry) addStoichiometry(basic, (Stoichiometry)e);
      else if (e instanceof DeltaG) addDeltaG(basic, (DeltaG)e);
      else if (e instanceof BioSource) addBioSource(basic, (BioSource)e);
      // References
      else if (e instanceof Evidence) addEvidence(basic, (Evidence)e);
      else if (e instanceof Provenance) addProvenance(basic, (Provenance)e);
      else if (e instanceof PublicationXref) addPublicationXref(basic, (PublicationXref)e);
      else if (e instanceof RelationshipXref) addRelationshipXref(basic, (RelationshipXref)e);
      else if (e instanceof UnificationXref) addUnificationXref(basic, (UnificationXref)e);
      // Extra elements that we currently do not handle
      else if (SeenButNotHandled.haveSeen(e)) addGeneric(basic);
      else {
        System.err.println("Unexpected BioPAX element: " + e + "\nOf class: " + e.getClass());
        System.exit(-1);
      }

    }
  }

  void addProtein(BPElement basics, Protein p) {
    Resource refToSeq;
    BioPAXElement entityRef = p.getEntityReference();
   
    if (entityRef != null) {
      refToSeq = new Resource(id(entityRef));
    } else {
      refToSeq = null;
      // Encountered for Example-Complex1: We cannot seem to locate the ref for the complex
      // that makes up a protein. The documentation says it should be in entityRef
      // and not the deprecated "getMemberPhysicalEntity" (which is also empty, btw)
      //    "Please avoid using this property in your BioPAX L3 models unless absolutely 
      //      sure/required, for there is an alternative way (using 
      //      PhysicalEntity/entityReference/memberEntityReference), and this will 
      //      probably be deprecated in the future BioPAX releases."
      // Where is the reference to the Complex?? BUG?
      System.out.println("---- There might be a BioPAX bug when the protein is a complex.");
      System.out.println("---- We cannot find the entityReference to the Complex that the"); 
      System.out.println("---- Protein is made of (instead of sequence), even though the");
      System.out.println("---- documentation says it should be there.");
    }

    act.installer.metacyc.entities.Protein protein =
        new act.installer.metacyc.entities.Protein(basics, refToSeq);

    this.organism.add(protein.getID(), protein);
  }

  void addRna(BPElement basics, Rna r) {
    Resource refToSeq = new Resource(id(r.getEntityReference()));
    Resource localization;
    
    if (r.getCellularLocation() == null) {
      localization = null; // compartment is not necessarily specified
    } else {
      localization = new Resource( id(r.getCellularLocation()) );
    }

    act.installer.metacyc.entities.RNA rna =
        new act.installer.metacyc.entities.RNA(basics, refToSeq, localization);
    this.organism.add(rna.getID(), rna);
  }

  // Both ProteinReference and RnaReference are subclasses of 
  // SequenceEntityReference, which provides the getSeq, getOrg methods
  void addProteinRnaReference(BPElement basics, SequenceEntityReference e) {
    String seq = e.getSequence();
    Resource org = new Resource( id(e.getOrganism()) );
    Set<Resource> memRef = mapToPtrs( e.getMemberEntityReference() );

    act.installer.metacyc.entities.ProteinRNARef ref =
      new act.installer.metacyc.entities.ProteinRNARef(basics, org, seq, memRef);
    this.organism.add(ref.getID(), ref);

    if (!quiet) System.out.println(ref.getStandardName());
  }

  void addSmallMolecule(BPElement basics, SmallMolecule e) {
    Resource smRef = mapToPtrs( e.getEntityReference() );
    Resource loc = mapToPtrs( e.getCellularLocation() );

    act.installer.metacyc.entities.SmallMolecule sm =
      new act.installer.metacyc.entities.SmallMolecule(basics, smRef, loc);
    this.organism.add(sm.getID(), sm);

    if (!quiet) System.out.println(sm.getStandardName());
  }

  void addSmallMoleculeReference(BPElement basics, SmallMoleculeReference e) {
    Set<Resource> memRefs = mapToPtrs( e.getMemberEntityReference() );
    Resource struc = mapToPtrs( e.getStructure() );
    Float molw = e.getMolecularWeight();

    act.installer.metacyc.entities.SmallMoleculeRef smref =
      new act.installer.metacyc.entities.SmallMoleculeRef(basics, memRefs, struc, molw);
    this.organism.add(smref.getID(), smref);

    if (!quiet) System.out.println(smref.getStandardName());
  }

  void addComplex(BPElement basics, Complex e) {
    Set<Resource> stoi = mapToPtrs( e.getComponentStoichiometry() );
    Set<Resource> comp = mapToPtrs( e.getComponent() );

    act.installer.metacyc.entities.Complex complex =
      new act.installer.metacyc.entities.Complex(basics, stoi, comp);
    this.organism.add(complex.getID(), complex);

    if (!quiet) System.out.println(complex.getStandardName());
  }

  void addChemicalStructure(BPElement basics, ChemicalStructure e) {
    StructureFormatType format = e.getStructureFormat();
    String data = e.getStructureData();
    data = data.replaceAll("&lt;", "<");
    data = data.replaceAll("&gt;", "<");
    data = data.replaceAll("&quot;", "\"");
    
    act.installer.metacyc.entities.ChemicalStructure struc =
      new act.installer.metacyc.entities.ChemicalStructure(basics, format, data);
    
    this.organism.add(struc.getID(), struc);

    if (!quiet) System.out.println(struc.getSMILES());
  }

  void addPathway(BPElement basics, Pathway e) {
    Set<Resource> order = mapToPtrs( e.getPathwayOrder() );
    Set<Resource> components = mapToPtrs( e.getPathwayComponent() );
    act.installer.metacyc.processes.Pathway pathway =
      new act.installer.metacyc.processes.Pathway(basics, order, components);
    this.organism.add(pathway.getID(), pathway);

    if (!quiet) System.out.println(pathway.getStandardName());
  }

  void addBiochemicalPathwayStep(BPElement basics, BiochemicalPathwayStep e) {
    StepDirection dir = e.getStepDirection();
    Resource conv = mapToPtrs( e.getStepConversion() );
    Set<Resource> proc = mapToPtrs( e.getStepProcess() );
    Set<Resource> next = mapToPtrs( e.getNextStep() );

    act.installer.metacyc.processes.BiochemicalPathwayStep step =
      new act.installer.metacyc.processes.BiochemicalPathwayStep(basics, dir, conv, proc, next);
    this.organism.add(step.getID(), step);

    if (!quiet) System.out.println(step.getConversion());
  }

  void addCatalysis(BPElement basics, Catalysis e) {
    CatalysisDirectionType dir = e.getCatalysisDirection();
    ControlType typ = e.getControlType();
    Set<Resource> controller = mapToPtrs( e.getController() );
    Set<Resource> controlled = mapToPtrs( e.getControlled() );
    Set<Resource> cofactors = mapToPtrs( e.getCofactor() );

    act.installer.metacyc.processes.Catalysis catalysis =
      new act.installer.metacyc.processes.Catalysis(basics, dir, typ, controller, controlled, cofactors);
    this.organism.add(catalysis.getID(), catalysis);

    if (!quiet) System.out.println(catalysis.getStandardName());
  }

  void addModulation(BPElement basics, Modulation e) {
    ControlType typ = e.getControlType();
    Set<Resource> controller = mapToPtrs( e.getController() );
    Set<Resource> controlled = mapToPtrs( e.getControlled() );

    act.installer.metacyc.processes.Modulation modulate =
      new act.installer.metacyc.processes.Modulation(basics, typ, controller, controlled);
    this.organism.add(modulate.getID(), modulate);

    if (!quiet) System.out.println(modulate.getStandardName());
  }

  // BiochemicalReaction, Transport, TransportWithBiochemicalReaction are 
  // subclasses of Conversion, in model.level3, and in our datamodel they are
  // annotated as different types of conversions using an Enum in Conversion
  void addConversion(BPElement basics, Conversion e) {
    Set<Resource> left = mapToPtrs( e.getLeft() );
    Set<Resource> right = mapToPtrs( e.getRight() );
    Set<Resource> stoi = mapToPtrs( e.getParticipantStoichiometry() );

    ConversionDirectionType dir = e.getConversionDirection(); 
    Boolean spont = e.getSpontaneous();

    // ec, deltaG are only set for BiochemicalReaction, for others they are empty
    Set<String> ec = new HashSet<String>();
    Set<Resource> deltaG = new HashSet<Resource>();

    act.installer.metacyc.processes.Conversion.TYPE type = null;
    if (e instanceof BiochemicalReaction) {
      ec = ((BiochemicalReaction)e).getECNumber();
      deltaG = mapToPtrs( ((BiochemicalReaction)e).getDeltaG() );
      type = act.installer.metacyc.processes.Conversion.TYPE.BIOCHEMICAL_RXN;
    } else if (e instanceof Transport) {
      type = act.installer.metacyc.processes.Conversion.TYPE.TRANSPORT;
    } else if (e instanceof TransportWithBiochemicalReaction) {
      type = act.installer.metacyc.processes.Conversion.TYPE.TRANSPORT_W_BIOCHEMICAL_RXN;
    }

    BPElement rxn = new act.installer.metacyc.processes.Conversion(basics, left,
        right, stoi, dir, spont, ec, deltaG, type);
    this.organism.add(rxn.getID(), rxn);

    if (!quiet) System.out.println(rxn.getStandardName());
  }

  // CellularLocationVocabulary, EvidenceCodeVocabulary, RelationshipTypeVocabulary 
  // are subclasses of ControlledVocabulary, which provides the Set<String> getTerm fn
  void addTerm(BPElement basics, ControlledVocabulary e) {
    Set<String> terms = e.getTerm();
    act.installer.metacyc.annotations.Term t =
      new act.installer.metacyc.annotations.Term(basics, terms);
    this.organism.add(t.getID(), t);

    if (!quiet) System.out.println(terms);
  }

  void addStoichiometry(BPElement basics, Stoichiometry e) {
    Resource physicalEntity = mapToPtrs( e.getPhysicalEntity() );
    float coeff = e.getStoichiometricCoefficient();

    act.installer.metacyc.annotations.Stoichiometry s =
      new act.installer.metacyc.annotations.Stoichiometry(basics, physicalEntity, coeff);
    this.organism.add(s.getID(), s);

    if (!quiet) System.out.println("coeff: " + s.getCoefficient() + " on " + s.getPhysicalEntity());
  }

  void addDeltaG(BPElement basics, DeltaG e) {
    Float dg = e.getDeltaGPrime0();

    act.installer.metacyc.annotations.DeltaG deltaG =
      new act.installer.metacyc.annotations.DeltaG(basics, dg);
    this.organism.add(deltaG.getID(), deltaG);

    if (!quiet) System.out.println(dg);
  }

  void addBioSource(BPElement basics, BioSource e) {
    act.installer.metacyc.annotations.BioSource org =
      new act.installer.metacyc.annotations.BioSource(basics);
    this.organism.add(org.getID(), org);

    if (!quiet) System.out.println(org.getName());
  }

  void addEvidence(BPElement basics, Evidence e) {
    Set<Resource> codes = mapToPtrs( e.getEvidenceCode() );
    act.installer.metacyc.references.Evidence evidence =
      new act.installer.metacyc.references.Evidence(basics, codes);
    this.organism.add(evidence.getID(), evidence);

    if (!quiet) System.out.println(evidence.getStandardName());
  }

  void addProvenance(BPElement basics, Provenance e) {
    act.installer.metacyc.references.Provenance provenance =
      new act.installer.metacyc.references.Provenance(basics);
    this.organism.add(provenance.getID(), provenance);

    if (!quiet) System.out.println(provenance.getStandardName());
  }

  void addPublicationXref(BPElement basics, PublicationXref e) {
    int yr = e.getYear();
    String title = e.getTitle();
    Set<String> src = e.getSource();
    String db = e.getDb();
    String id = e.getId();
    Set<String> auth = e.getAuthor();

    act.installer.metacyc.references.Publication pubs =
      new act.installer.metacyc.references.Publication(basics, yr, title, src, db, id, auth);
    this.organism.add(pubs.getID(), pubs);

    if (!quiet) System.out.println(title + " by " + auth);
  }

  void addRelationshipXref(BPElement basics, RelationshipXref e) {
    Resource term = mapToPtrs( e.getRelationshipType() );
    String db = e.getDb();
    String id = e.getId();

    act.installer.metacyc.references.Relationship reln =
      new act.installer.metacyc.references.Relationship(basics, term, db, id);
    this.organism.add(reln.getID(), reln);

    // if (!quiet) System.out.format("reln %s:%s of type: %s\n", db, id, term);
  }

  void addUnificationXref(BPElement basics, UnificationXref e) {
    String db = e.getDb();
    String id = e.getId();

    act.installer.metacyc.references.Unification unificationXref =
      new act.installer.metacyc.references.Unification(basics, db, id);
    this.organism.add(unificationXref.getID(), unificationXref);

    // if (!quiet) System.out.format("unif %s:%s\n", db, id);
  }

  void addGeneric(BPElement basics) {
    BPElement bpe = new BPElement(basics);
    this.organism.add(bpe.getID(), bpe);

    if (!quiet) System.out.println("Added without reading details: " + bpe.getID());
  }

  static String id(BioPAXElement bpe) {
    return bpe.getRDFId();
  }

  private BPElement getBasicData(BioPAXElement e) {
    Resource id = new Resource( id(e) );
    String standardName = null; // getStandardName() in level3.Named
    String displayName = null;  // getDisplayName() in level3.Named
    Set<String> name = null;    // getName() in level3.Named
    Set<Resource> xrefs = null;      // getXref() in level3.XReferrable
    Set<Resource> dataSource = null; // getDataSource() in level3.Entity
    Set<Resource> evidence = null;   // getEvidence() in level3.Observable
    Set<String> comment = null; // getComment in level3.Level3Element

    if (e instanceof Named) {
      Named n = (Named)e;
      standardName = n.getStandardName();
      displayName = n.getDisplayName();
      name = n.getName();
    }
    if (e instanceof XReferrable) {
      xrefs = mapToPtrs( ((XReferrable)e).getXref() ); 
    }
    if (e instanceof Entity) {
      dataSource = mapToPtrs( ((Entity)e).getDataSource() ); // returns the Provenance entries
    }
    if (e instanceof Observable) {
      evidence = mapToPtrs( ((Observable)e).getEvidence() ); // returns the Evidence entries
    }
    if (e instanceof Level3Element) {
      comment = ((Level3Element)e).getComment();
    }

    return new BPElement(id, standardName, displayName, name, xrefs, dataSource, evidence, comment);
  }

  private Resource mapToPtrs(BioPAXElement e) {
    if (e == null) return null;
    return new Resource(e.getRDFId());
  }

  private Set<Resource> mapToPtrs(Set s) {
    if (s == null) return null;
    Set<Resource> r = new HashSet<Resource>();
    for (Object e : s) 
      r.add(new Resource(((BioPAXElement)e).getRDFId()));
    return r;
  }
}

/*
   ===========================================================================
Example-Complex1:
Source: ano2cyc/biopax-level3.owl

  <bp:Protein rdf:ID="Protein53715">
    <bp:xref rdf:resource="#UnificationXref53716"/>
    <bp:standardName rdf:datatype="http://www.w3.org/2001/XMLSchema#string">a glucosyl-glycogenin</bp:standardName>
    <bp:entityReference>
      <bp:Complex rdf:ID="Complex53711">
        <bp:xref rdf:resource="#UnificationXref53713"/>
        <bp:xref rdf:resource="#UnificationXref53712"/>
        <bp:standardName rdf:datatype="http://www.w3.org/2001/XMLSchema#string">a glycogenin</bp:standardName>
        <bp:dataSource rdf:resource="#Provenance30449"/>
      </bp:Complex>
    </bp:entityReference>
    <bp:dataSource rdf:resource="#Provenance30449"/>
    <bp:cellularLocation rdf:resource="#CellularLocationVocabulary30461"/>
  </bp:Protein>
   ===========================================================================
   ===========================================================================
   ===========================================================================
*/
