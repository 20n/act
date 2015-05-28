package interview

import scala.io.Source
import scala.collection.JavaConverters._
import com.ggasoftware.indigo.Indigo
import com.ggasoftware.indigo.IndigoObject
import com.ggasoftware.indigo.IndigoRenderer

import act.shared.helpers.P
import act.server.Molecules.DotNotation
import act.server.Molecules.SMILES

object subgraph {

  // indigo is a (slightly buggy) library that handles some 
  // operations over chemicals, e.g., reading SMILES, atom-to-atom maps
  val indigo = new Indigo

  // set logging level to only severe messages
  act.server.Logger.setMaxImpToShow(0)

  def main(args: Array[String]) {

    if (args.length == 0) {
      println("Usage sbt \"runMain com.act.interview.subgraph /path/data\"");
      System.exit(-1)
    }

    // data files in input are expected to contain one reaction per line
    for (line <- Source.fromFile(args(0)).getLines()) {
      if (line.length > 0 && line(0) != '#') {
      
        // the format of the reaction: 
        //  ID tab IN_chemicals tab OUT_chemicals
        val rxn = line split "\t"
        
        // extract the ID
        val id = rxn(0)
        
        // extract the IN and OUT chemicals (space separated)
        val incoming = rxn(1) split "\\+"
        val outgoing = rxn(2) split "\\+"

        // debug dump to stdout to check the data read
        println("rxn: " + incoming.mkString("+") + ">>" + outgoing.mkString("+"))

        // compute the number of chemicals on each side of the reaction
        val in_sz = incoming.length
        val out_sz = outgoing.length

        // the reactions dont need to be 1 to 1.
        // 0, 1 => bad data in either missing incoming or outgoing chemicals
        // 2    => 1 to 1 reactions
        // >2   => >1 chemical in either in/out. more involved handling needed
        (in_sz + out_sz) match {
          case 0 | 1  => {
            // bad data: missing substrate/product -> ignore; do nothing
          }
          case 2      => {
            // 1 to 1, simple handling
            aam(id, incoming(0), outgoing(0))
          }
          case _      => {
            // >1 chemical in either in/out
            general_aam(id, incoming, outgoing)
          }
        }
      }
    }

  }

  def aam(id: String, in: String, out: String) {
    
    // dump pretty reaction image as pretty_rxn_id.png
    renderUnannotatedRxn(id, in, out)
    
    /* ***************************************************
     * We will first do the subgraph atom 2 atom mapping
     * using the (buggy) indigo library. That gives us a 
     * baseline to compare against
     * ***************************************************/
  
    // do maximal subgraph matching using library
    val r_library = aam_library(in, out)

    // debug print to stdout
    printRxn(r_library)
    
    // debug render a PNG image of the reaction
    render(r_library, "library_rxn_" + id + ".png", indigo)


    /* ***************************************************
     * Next we do the subgraph atom 2 atom mapping using
     * our custom implementation. Then we should compare.
     * ***************************************************/

    // do maximal subgraph matching using customer implementation
    val r_custom = aam_custom(in, out)

    // debug print rxn to stdout
    printRxn(r_custom)

    // debug render a PNG image of the reaction
    render(r_custom, "custom_rxn_" + id + ".png", indigo)
  }

  def aam_library(in: String, out: String) = {

    // rxns, molecules, atoms are stored as IndigoObject's
    val rxn = rxn_object(in, out)

    // in the rxn, assign mappings to atoms between in and out chemicals
    rxn.automap("keep")

    SMILES.fixUnmappedAtoms(rxn)

    // return the annotated reaction
    rxn
  }

  def aam_custom(in: String, out: String) = {

    // rxns, molecules, atoms are stored as IndigoObject's
    val rxn = rxn_object(in, out)

    // do the subgraph matching
    val mapping: Map[Int, Map[Int, Int]] = subgraph_mapping(rxn)

    // annotate the reaction with the discovered mapping
    // so that we can see the results in the PNG images
    install_atommap(rxn, mapping)

    // return the annotated reaction
    rxn
  }

  def subgraph_mapping(r: IndigoObject) = {

    // the inferred map with be a map of 
    // molid (within rxn) -> (map from atomid -> atomid)
    var map = Map[Int, Map[Int, Int]]()

    // iterate over all molecules in reaction
    for (idx <- 0 until r.countMolecules) {

      // get the molecule object within the reaction
      val m = r.getMolecule(idx)

      // init a mapping that will hold for each atomid its map#
      var atoms_mapped = Map[Int, Int]()
    
      // iterate over all atoms within the molecule
      for (idx <- 0 until m.countAtoms) {

        // this atom maps to some map# that is to be inferred
        // TODO: FIND the map# for each atom, based on computing
        // a subgraph isomorphism match
        val fake_mapping = 1 // idx + 1
      
        atoms_mapped += (idx -> fake_mapping)

      }

      // store the mapping of atoms -> map# for this molecule
      map += (m.index -> atoms_mapped)
    }

    // return the map
    map
  }

  def rxn_object(in: String, out: String) = {
    // P is a wrapper class for pairs
    // Encapsulate a rxn as a pair(list(in chemical), list(out chemical))
    val rxn_str = new P(List(in).asJava, List(out).asJava)

    // Dot Notation is a 20n internal notation for reducing
    // all double/triple bonds to single bond graphs
    val rxn = DotNotation.ToDotNotationRxn(rxn_str, indigo) 
  
    // typical representations ignore hydrogens, expand them out
    rxn.unfoldHydrogens()

    // return reaction object
    rxn
  }

  def unannotated_rxn_object(ins: List[String], outs: List[String]) = {
    // create an empty reaction object
    val rxn = indigo.createReaction
  
    // add each of the incoming chemicals to the reaction
    for (in <- ins)
      rxn.addReactant(indigo.loadMolecule(in)) 

    // add each of the outgoing chemicals to the reaction
    for (out <- outs)
      rxn.addProduct(indigo.loadMolecule(out)) 

    // return reaction object
    rxn
  }

  def install_atommap(r: IndigoObject, map: Map[Int, Map[Int, Int]]) {

    // iterate over all molecules in the reaction
    for (idx <- 0 until r.countMolecules) {
      
      // get a specific molecule
      val m = r.getMolecule(idx)
  
      // get the mapping for all its atoms that we want to install
      val mapping = map(m.index)

      // iterate over all atoms in the molecule
      for (idx <- 0 until m.countAtoms) {

        // get the atom mapping number for this atom
        val aam = mapping(idx)
        
        // get the atom
        val atom = m.getAtom(idx)

        // install the atom mapping # onto the atom
        r.setAtomMappingNumber(atom, aam)
      }
    }
  }

  def render(rxn: IndigoObject, fname: String, indigo: Indigo) {

    // indigo provides a convenient renderer
    val renderer = new IndigoRenderer(indigo)

    // output can be in many formats; most convenient is png
    indigo.setOption("render-output-format", "png")
    
    // internal computation of the layout of atoms
    rxn.layout

    // write the image to file
    renderer.renderToFile(rxn, fname)
  }

  def printRxn(r: IndigoObject) {

    // function that print a molecule, contained within a reaction
    def printMol(m: IndigoObject, r: IndigoObject) {

      // iterate over all atoms of the molecule
      for (idx <- 0 until m.countAtoms) {
    
        // get the atom of the molecule; and print its symbol
        // also, the atom might have an atom mapping #
        val atom = m.getAtom(idx)
        println("\t atom(" + idx + ") = " + atom.symbol 
                + " aam[" + r.atomMappingNumber(atom) + "]")

      }

      // iterate over all bonds of the molecule
      for (idx <- 0 until m.countBonds) {

        // get the src, dst, bondtype; and print them
        val bond = m.getBond(idx)
        val src = bond.source.symbol
        val dst = bond.destination.symbol
        val typ = bond.bondOrder match {
          case 1 => "-"
          case 2 => "="
          case 3 => "#"
        }
        println("\t bond(" + idx + ") = " + src + typ + dst)
      }
    }

    // iterate over all molecules in rxn and print each
    for (m <- r.iterateMolecules.asInstanceOf[java.util.Iterator[IndigoObject]].asScala) {
      println("Molecule:")
      printMol(m, r)
    }
      
  }

  def renderUnannotatedRxn(id: String, in: String, out: String) {
    val unexpand_rxn = unannotated_rxn_object(List(in), List(out))

    render(unexpand_rxn, "pretty_rxn_" + id + ".png", indigo)
  }

  def general_aam(id: String, in: Array[String], out: Array[String]) {
  }

}
