package act.shared

object ChemicalSymbols {

  sealed trait Atom { def symbol: Char; def monoIsotopicMass: Double }
  case object C extends Atom { val symbol = 'C'; val monoIsotopicMass = 12.00000 }
  case object H extends Atom { val symbol = 'H'; val monoIsotopicMass = 1.007825 }
  case object O extends Atom { val symbol = 'O'; val monoIsotopicMass = 15.994915 }
  case object N extends Atom { val symbol = 'N'; val monoIsotopicMass = 14.003074 }
  case object P extends Atom { val symbol = 'P'; val monoIsotopicMass = 30.973761 }
  case object S extends Atom { val symbol = 'S'; val monoIsotopicMass = 31.972071 }
  
  abstract class AminoAcid {
    def name: String
    def symbol: Char
    def formula: String
    def monoIsotopicMass: Double
    def elems: Map[Atom, Int]
  }
  
  case object Gly extends AminoAcid {
    val name = "Glycine"; val symbol = 'G'; val formula = "C2H5NO2"; val monoIsotopicMass = 75.032028;
    val elems: Map[Atom, Int] = Map(C->2, H->5, O->2, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.730.html"
  }
  case object Ala extends AminoAcid {
    val name = "Alanine"; val symbol = 'A'; val formula = "C3H7NO2"; val monoIsotopicMass = 89.047676;
    val elems: Map[Atom, Int] = Map(C->3, H->7, O->2, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.582.html"
  }
  case object Pro extends AminoAcid {
    val name = "Proline"; val symbol = 'P'; val formula = "C5H9NO2"; val monoIsotopicMass = 115.063332;
    val elems: Map[Atom, Int] = Map(C->5, H->9, O->2, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.594.html"
  }
  case object Val extends AminoAcid {
    val name = "Valine"; val symbol = 'V'; val formula = "C5H11NO2"; val monoIsotopicMass = 117.078979;
    val elems: Map[Atom, Int] = Map(C->5, H->11, O->2, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.1148.html"
  }
  case object Cys extends AminoAcid {
    val name = "Cysteine"; val symbol = 'C'; val formula = "C3H7NO2S"; val monoIsotopicMass = 121.019745;
    val elems: Map[Atom, Int] = Map(C->3, H->7, O->2, N->1, S->1);
    val url = "http://www.chemspider.com/Chemical-Structure.574.html"
  }
  case object Ile extends AminoAcid {
    val name = "Isoleucine"; val symbol = 'I'; val formula = "C6H13NO2"; val monoIsotopicMass = 131.094635;
    val elems: Map[Atom, Int] = Map(C->6, H->13, O->2, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.769.html"
  }
  case object Leu extends AminoAcid {
    val name = "Leucine"; val symbol = 'L'; val formula = "C6H13NO2"; val monoIsotopicMass = 131.094635;
    val elems: Map[Atom, Int] = Map(C->6, H->13, O->2, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.834.html"
  }
  case object Met extends AminoAcid {
    val name = "Methionine"; val symbol = 'M'; val formula = "C5H11NO2S"; val monoIsotopicMass = 149.051056;
    val elems: Map[Atom, Int] = Map(C->5, H->11, O->2, N->1, S->1);
    val url = "http://www.chemspider.com/Chemical-Structure.853.html"
  }
  case object Phe extends AminoAcid {
    val name = "Phenylalanine"; val symbol = 'F'; val formula = "C9H11NO2"; val monoIsotopicMass = 165.078979;
    val elems: Map[Atom, Int] = Map(C->9, H->11, O->2, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.5910.html"
  }
  case object Ser extends AminoAcid {
    val name = "Serine"; val symbol = 'S'; val formula = "C3H7NO3"; val monoIsotopicMass = 105.042595;
    val elems: Map[Atom, Int] = Map(C->3, H->7, O->3, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.597.html"
  }
  case object Thr extends AminoAcid {
    val name = "Threonine"; val symbol = 'T'; val formula = "C4H9NO3"; val monoIsotopicMass = 119.058243;
    val elems: Map[Atom, Int] = Map(C->4, H->9, O->3, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.6051.html"
  }
  case object Tyr extends AminoAcid {
    val name = "Tyrosine"; val symbol = 'Y'; val formula = "C9H11NO3"; val monoIsotopicMass = 181.073898;
    val elems: Map[Atom, Int] = Map(C->9, H->11, O->3, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.5833.html"
  }
  case object Asp extends AminoAcid {
    val name = "Aspartate"; val symbol = 'D'; val formula = "C4H7NO4"; val monoIsotopicMass = 133.037506;
    val elems: Map[Atom, Int] = Map(C->4, H->7, O->4, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.411.html"
  }
  case object Glu extends AminoAcid {
    val name = "Glutamate"; val symbol = 'E'; val formula = "C5H9NO4"; val monoIsotopicMass = 147.053162;
    val elems: Map[Atom, Int] = Map(C->5, H->9, O->4, N->1, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.30572.html"
  }
  case object Lys extends AminoAcid {
    val name = "Lysine"; val symbol = 'K'; val formula = "C6H14N2O2"; val monoIsotopicMass = 146.10553;
    val elems: Map[Atom, Int] = Map(C->6, H->14, O->2, N->2, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.843.html"
  }
  case object Trp extends AminoAcid {
    val name = "Tryptophan"; val symbol = 'W'; val formula = "C11H12N2O2"; val monoIsotopicMass = 204.089874;
    val elems: Map[Atom, Int] = Map(C->11, H->12, O->2, N->2, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.1116.html"
  }
  case object Asn extends AminoAcid {
    val name = "Asparagine"; val symbol = 'N'; val formula = "C4H8N2O3"; val monoIsotopicMass = 132.053497;
    val elems: Map[Atom, Int] = Map(C->4, H->8, O->3, N->2, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.231.html"
  }
  case object Gln extends AminoAcid {
    val name = "Glutamine"; val symbol = 'Q'; val formula = "C5H10N2O3"; val monoIsotopicMass = 146.069138;
    val elems: Map[Atom, Int] = Map(C->5, H->10, O->3, N->2, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.718.html"
  }
  case object His extends AminoAcid {
    val name = "Histidine"; val symbol = 'H'; val formula = "C6H9N3O2"; val monoIsotopicMass = 155.069473;
    val elems: Map[Atom, Int] = Map(C->6, H->9, O->2, N->3, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.6038.html"
  }
  case object Arg extends AminoAcid {
    val name = "Arginine"; val symbol = 'R'; val formula = "C6H14N4O2"; val monoIsotopicMass = 174.111679;
    val elems: Map[Atom, Int] = Map(C->6, H->14, O->2, N->4, S->0);
    val url = "http://www.chemspider.com/Chemical-Structure.227.html"
  }

  val AllAminoAcids = List(Gly, Ala, Pro, Val, Cys, Ile, Leu, Met, Phe, Ser,
                           Thr, Tyr, Asp, Glu, Lys, Trp, Asn, Gln, His, Arg)

  object Helpers {
    def fromSymbol(sym: Char): AminoAcid = ChemicalSymbols.AllAminoAcids.find(_.symbol.equals(sym)) match {
      case Some(aa) => aa
      case None => throw new Exception("Invalid symbol for an amino acid.")
    }

    val atomOrderInFormula = List(C, H, N, O, S, P)
    def computeFormulaFromElements(elems: Map[Atom, Int]) = {
      // for each pair such as (C, 2) and (N, 5) specified in the elemental composition of an AA, first
      // convert it `C2` and `N5` (the `.map` below), and then concatenate them together (the `.reduce` below)
      val elemnum: Map[Atom, String] = elems.map{
        case (atom, 0) => (atom, "")
        case (atom, 1) => (atom, atom.symbol.toString)
        case (atom, num) => (atom, atom.symbol + num.toString)
      }

      atomOrderInFormula.map{ case atom =>
        elemnum.get(atom) match {
          case Some(elemN) => elemN
          case None => throw new Exception("formula does not have one of CHNOS specified")
        }
      }.reduce(_ + _)
    }

    def computeMassFromAtomicFormula(elems: Map[Atom, Int]): Double = {
      // for each pair such as (C, 2) specified in the elemental composition of an AA, first convert
      // it `massOf(C) * 2` (the `.map` below), and then add them together (the `.reduce` below)
      elems.map{ case (atom, num) => atom.monoIsotopicMass * num }.reduce(_ + _)
    }
  }
}

