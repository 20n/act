package act.installer.bing

class costmodel(modelName: String) {

  type Liter = Double
  type GramPerLiter = Double
  type Percentage = Double
  type Hour = Double
  type DegreeCentigrade = Double
  type Ratio = Double
  type GramPerKilogram = Double
  type KiloWattPerMeterCubed = Double
  type KiloJoulePerMMol = Double
  type MMolPerLiterHour = Double

  def main(args: Array[String]) {
  }

  def getPerKgCost(strainYield: Percentage, strainTiter: GramPerLiter) = 670.0

  var strainTiter = constants.DefaultTiter;
  var strainYield = constants.DefaultYield;
  var fermRunTime = constants.DefaultFermRunTime;
  var location: constants.Location = constants.GER;

  object constants {
    // Default titers and yields are instances for Shikimate from review paper:
    // "Recombinant organisms for production of industrial products" 
    // --- http://www.ncbi.nlm.nih.gov/pmc/articles/PMC3026452
    val DefaultTiter: GramPerLiter = 84.0; // 84 g/L
    val DefaultYield: Percentage = 31.9; // 31.9% g/g
    val DefaultFermRunTime: Hour = 5 * 24; // 5 days

    sealed trait Location { def name: String; def rentalRate: Double }
    // See email thread "Model" between Saurabh, Jeremiah, Tim Revak for cost quotes from various places
    case object GER extends Location { val name = "Germany"; val rentalRate = 5.0 }
    case object ITL extends Location { val name = "Italy"; val rentalRate = 8.0 }
    case object IND extends Location { val name = "India"; val rentalRate = 10.0 }
    case object CHN extends Location { val name = "China"; val rentalRate = 5.3 }
    case object MID extends Location { val name = "Midwest"; val rentalRate = 8.3 }
    case object MEX extends Location { val name = "Mexico"; val rentalRate = 8.3 }

    /************************************ Fermentation ****************************************/
    // Productivity and Titer
    val vesselSize: Liter = 200.0 * 1000 // 200m3
    val hoursPerYear: Hour = 24 * 350 // all plants have two week downtime, so 365-14 days
    val fermTemp: DegreeCentigrade = 25 // degreeC
    val hoursForCIP: Hour = 12 // 12 hours to Clean In Place
    val finalByInitialVol: Ratio = 2.0 // depends on the number of draws
    val finalDryCellWeight: GramPerKilogram = 170 // g/kg
    val fermenterAspect: Ratio = 3.0 // height/diameter
    // Compressor Power
    val airFlow: Ratio = 2.0
    // Agitator Power
    val agitationRate: KiloWattPerMeterCubed = 0.75
    // Microbial Heat
    val oxygenUptakeRate: MMolPerLiterHour = 120.0 // OUR in mmol O2/L/hr
    val microbialHeatGen: KiloJoulePerMMol = 0.52 // kJ/mmol O2
    // Ammonia Use
    val ammoniaPerGlucose: Ratio = 0.06
    // Steam Use

  }


}
