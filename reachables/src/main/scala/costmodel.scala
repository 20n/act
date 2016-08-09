package act.installer.bing

class costmodel(modelName: String) {

  // Better utilize the type system to enforce type constraints 
  // e.g., USD / Kg = USDPerKg, LiterDay = Liter * Day
  type Liter = Double
  type Kg = Double
  type GramPerLiter = Double
  type GramPerKg = Double
  type USD = Double
  type Cents = Double
  type USDPerKg = Double
  type USDPerTon = Double
  type USDPerBatch = Double
  type KgPerBatch = Double
  type Percentage = Double
  type Hour = Double
  type Day = Double
  type Year = Double
  type LiterDay = Double
  type DegreeCentigrade = Double
  type Ratio = Double
  type KiloWattPerMeterCubed = Double
  type KiloJoulePerMMol = Double
  type MMolPerLiterHour = Double

  /********************************************************************************************
  *  Unit Conversions 
  ********************************************************************************************/

  val Times1000 = { x: Double => x * 1000 }
  val By1000 = { x: Double => x/1000 }
  val By100 = { x: Double => x/100 }
  val DaysToHours = { x: Double => 24 * x }
  val LiterToKg = { x: Double => x }
  val GramToKg = By1000
  val MgToGram = By1000
  val KgToTon = By1000
  val CentsToUSD = By100
  val M3toLiter = Times1000
  val PercentToRatio = By100

  /********************************************************************************************
  *  Constants 
  ********************************************************************************************/

  // Default titers and yields are instances for Shikimate from review paper:
  // "Recombinant organisms for production of industrial products" 
  // --- http://www.ncbi.nlm.nih.gov/pmc/articles/PMC3026452
  val DefaultTiter: GramPerLiter = 84.0; // 84 g/L
  val DefaultYield: Percentage = 31.9; // 31.9% g/g
  val DefaultFermRunTime: Day = 10
  val DefaultBrothVolumePerBatch: Kg = LiterToKg(M3toLiter(360))

  sealed trait Location { def name: String; def rentalRate: Cents }
  // See email thread "Model" between Saurabh, Jeremiah, Tim Revak for cost quotes from various places
  case object GER extends Location { val name = "Germany"; val rentalRate = 5.0 }
  case object ITL extends Location { val name = "Italy"; val rentalRate = 8.0 }
  case object IND extends Location { val name = "India"; val rentalRate = 10.0 }
  case object CHN extends Location { val name = "China"; val rentalRate = 5.3 }
  case object MID extends Location { val name = "Midwest"; val rentalRate = 8.3 }
  case object MEX extends Location { val name = "Mexico"; val rentalRate = 8.3 }

  /************************************ Fermentation ****************************************/
  // Productivity and Titer
  val vesselSize: Liter = M3toLiter(200.0)
  val pcOfVesselUsed: Percentage = 90.0
  val hoursPerYear: Hour = DaysToHours(350) // all plants have two week downtime, so 365-14 days
  val fermTemp: DegreeCentigrade = 25 
  val hoursForCIP: Hour = 12 // Clean In Place
  val finalDryCellWeight: GramPerKg = 170
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
  val initialTemp: DegreeCentigrade = 20
  val sterilizedTemp: DegreeCentigrade = 121

  // Labor
  val numOfEmployees: Integer = 14
  // Capital
  val DepreciationLife: Year = 15

  // Media composition and unit prices
  sealed trait MediaComp { def n: String; def amount: GramPerKg; def cost: USDPerKg }
  case object KH2PO4      extends MediaComp { val n = "KH2PO4";       val amount = 08.0; val cost = 1.05 }
  case object MgSO47H2O   extends MediaComp { val n = "MgSO4.7H2O";   val amount = 06.0; val cost = 0.10 }
  case object NH42SO4     extends MediaComp { val n = "(NH4)2 SO4";   val amount = 15.0; val cost = 0.09 }

  case object EDTA        extends MediaComp { val n = "EDTA";         val amount = MgToGram(800); val cost =  1.00 }
  case object FeSO47H2O   extends MediaComp { val n = "FeSO4.7H2O";   val amount = MgToGram(28);  val cost =  0.30 }
  case object ZnSO47H2O   extends MediaComp { val n = "ZnSO4.7H2O";   val amount = MgToGram(57.5);val cost =  0.45 }
  case object CaCl22H2O   extends MediaComp { val n = "CaCl2.2H2O";   val amount = MgToGram(29);  val cost =  0.14 }
  case object CuSO4       extends MediaComp { val n = "CuSO4 ";       val amount = MgToGram(3.2); val cost =  2.60 }
  case object Na2MoO42H2O extends MediaComp { val n = "Na2MoO4.2H2O"; val amount = MgToGram(4.8); val cost = 12.00 }
  case object CoCl26H20   extends MediaComp { val n = "CoCl2.6H20";   val amount = MgToGram(4.7); val cost =  9.00 }
  case object MnCl24H2O   extends MediaComp { val n = "MnCl2.4H2O";   val amount = MgToGram(3.2); val cost =  1.50 }
  case object Biotin      extends MediaComp { val n = "Biotin";       val amount = MgToGram(0.6); val cost =  0.50 }
  case object CaPantothe  extends MediaComp { val n = "CaPantothenate"; val amount = MgToGram(12);val cost = 20.00 }
  case object NicotinicAc extends MediaComp { val n = "NicotinicAcid"; val amount = MgToGram(12); val cost = 15.00 }
  case object Myoinositol extends MediaComp { val n = "Myoinositol";  val amount = MgToGram(30);  val cost =  9.00 }
  case object ThiamineHCl extends MediaComp { val n = "ThiamineHCl";  val amount = MgToGram(12);  val cost = 35.00 }
  case object PyroxidolHC extends MediaComp { val n = "PyroxidolHCl"; val amount = MgToGram(12);  val cost = 20.00 }

  val allMediaComponents = List(KH2PO4, MgSO47H2O, NH42SO4, EDTA, FeSO47H2O, ZnSO47H2O, CaCl22H2O, CuSO4, Na2MoO42H2O, CoCl26H20, MnCl24H2O, Biotin, CaPantothe, NicotinicAc, Myoinositol, ThiamineHCl, PyroxidolHC)
  val unitCostOfMedia: USDPerKg = allMediaComponents.map(x => x.cost * GramToKg(x.amount)).reduce(_ + _)

  case object Ammonia     { val n = "Ammonia"; val cost = 1.00 }
  case object Glucose     { val n = "Glucose"; val cost = 0.32 }
  case object Antifoam    { val n = "Antifoam"; }

  /********************************************************************************************
  *  Sensible defaults and external caller 
  ********************************************************************************************/

  var strainTiter: GramPerLiter = DefaultTiter;
  var strainYield: Percentage = DefaultYield;
  var fermRunTime: Day = DefaultFermRunTime;
  var brothVolumePerBatch: Kg = DefaultBrothVolumePerBatch
  var location: Location = GER;

  def getPerTonCost(yields: Percentage, titers: GramPerLiter): USDPerTon = {
    strainTiter = titers
    strainYield = yields
    RentalModelCost()
  }

  /********************************************************************************************
  *  Rental Model  
  ********************************************************************************************/
  def workingVolume: Liter = vesselSize * PercentToRatio(pcOfVesselUsed) 
  def finalByInitialVol: Ratio = brothVolumePerBatch / LiterToKg(workingVolume)
  def literDaysPerBatch: LiterDay = fermRunTime * vesselSize 
  def productPerBatch: KgPerBatch = GramToKg(brothVolumePerBatch * strainTiter)
  def glcConsumedPerBatch: KgPerBatch = productPerBatch / PercentToRatio(strainYield)
  def glcBatchedPerBatch: KgPerBatch = 0 // Glc in media = 0kgs: TODO: check if this is correct
  def glcFedPerBatch: KgPerBatch = glcConsumedPerBatch - glcBatchedPerBatch
  def ammoniaUsedPerBatch: KgPerBatch = ammoniaPerGlucose * glcConsumedPerBatch

  def rentPerBatch: USDPerBatch = literDaysPerBatch * CentsToUSD(location.rentalRate)
  def mediaPerBatch: USDPerBatch = brothVolumePerBatch * unitCostOfMedia
  def glcPerBatch: USDPerBatch = Glucose.cost * glcFedPerBatch
  def ammoniaPerBatch: USDPerBatch = Ammonia.cost * ammoniaUsedPerBatch 

  def RentalModelCost(): USDPerTon = {

    // brothvol = working vol + num_draws * (working vol)/2 => num_draws = 2(brothvol/working - 1)
    def numDraws: Ratio = 2 * (finalByInitialVol - 1)
    // final volume depends on num of draws, and there cannot be more than one draw a day (can there?)
    // which puts an upper bound on the final volume you can extract from a fermentation over x days
    // Do a sanity check that we did not get a input "final volume" which is insane compared to runtime
    assert(numDraws <= fermRunTime)

    val costPerBatch: USDPerBatch = rentPerBatch + mediaPerBatch + glcPerBatch + ammoniaPerBatch
    val costPerTon: USDPerTon = costPerBatch / KgToTon(productPerBatch)
    costPerTon
  }

}
