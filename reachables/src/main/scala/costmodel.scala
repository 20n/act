package act.installer.bing

import squants.time._
import squants.time.TimeConversions.TimeConversions

import squants.mass._
import squants.mass.MassConversions.MassConversions
import squants.space._
import squants.space.VolumeConversions.VolumeConversions
import squants.Dimensionless
import squants.DimensionlessConversions._

import squants.market.Money
import squants.market.USD
import squants.market.Price
import squants.Ratio
import squants.thermal.Temperature
import squants.thermal.TemperatureConversions._

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
  type KiloWattPerMeterCubed = Double
  type KiloJoulePerMMol = Double
  type MMolPerLiterHour = Double

  /********************************************************************************************
  *  Unit Conversions 
  ********************************************************************************************/

  case class Titer(base: Mass, counter: Volume) extends Ratio[Mass, Volume]
  case class Yield(base: Mass, counter: Mass) extends Ratio[Mass, Mass]

  val Times1000 = { x: Double => x * 1000 }
  val By1000 = { x: Double => x/1000 }
  val By100 = { x: Double => x/100 }
  val DaysToHours = { x: Double => 24 * x }
  def VolumeToMass(x: Volume): Mass = x * (1.kilograms / 1.litres)
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
  val DefaultTiter: Titer = Titer(84.0 grams, 1 litres) // 84 g/L
  val DefaultYield: Yield = Yield(31.9 grams, 100 grams) // 31.9% g/g
  val DefaultFermRunTime: Time = 10 days
  val DefaultBrothMassPerBatch: Mass = VolumeToMass(360 cubicMeters)

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
  val vesselSize: Volume = 200 cubicMeters
  val pcOfVesselUsed: Dimensionless = 90.0 percent
  val hoursPerYear: Hour = (350 days) to Hours // all plants have two week downtime, so 365-14 days
  val fermTemp: Temperature = 25 C
  val hoursForCIP: Hour = 12 // Clean In Place
  val finalDryCellWeight: GramPerKg = 170
  val fermenterAspect: Dimensionless = 3.0 // height/diameter
  // Compressor Power
  val airFlow: Dimensionless = 2.0 
  // Agitator Power
  val agitationRate: KiloWattPerMeterCubed = 0.75
  // Microbial Heat
  val oxygenUptakeRate: MMolPerLiterHour = 120.0 // OUR in mmol O2/L/hr
  val microbialHeatGen: KiloJoulePerMMol = 0.52 // kJ/mmol O2
  // Ammonia Use
  val ammoniaPerGlucose: Dimensionless = 6 percent
  // Steam Use
  val initialTemp: DegreeCentigrade = 20
  val sterilizedTemp: DegreeCentigrade = 121

  // Labor
  val numOfEmployees: Integer = 14
  // Capital
  val DepreciationLife: Year = 15

  // Media composition and unit prices
  sealed trait MediaComp { def n: String; def amount: Ratio[Mass, Mass]; def cost: Price[Mass] }
  case class mediaElem(traceAmount: Mass) extends Ratio[Mass, Mass] {
    def base = traceAmount
    def counter = 1 kg

    def *(that: Price[Mass]): Price[Mass] = Price(that * traceAmount, counter)
  }
  def alibaba(price: Double): Price[Mass] = USD(price) / Kilograms(1)
  case object KH2PO4      extends MediaComp { val n = "KH2PO4";       val amount = mediaElem(08.0 g); val cost = alibaba(1.05) }
  case object MgSO47H2O   extends MediaComp { val n = "MgSO4.7H2O";   val amount = mediaElem(06.0 g); val cost = alibaba(0.10) }
  case object NH42SO4     extends MediaComp { val n = "(NH4)2 SO4";   val amount = mediaElem(15.0 g); val cost = alibaba(0.09) }

  case object EDTA        extends MediaComp { val n = "EDTA";         val amount = mediaElem(800 mg); val cost =  alibaba(1.00) }
  case object FeSO47H2O   extends MediaComp { val n = "FeSO4.7H2O";   val amount = mediaElem(28 mg);  val cost =  alibaba(0.30) }
  case object ZnSO47H2O   extends MediaComp { val n = "ZnSO4.7H2O";   val amount = mediaElem(57.5 mg);val cost =  alibaba(0.45) }
  case object CaCl22H2O   extends MediaComp { val n = "CaCl2.2H2O";   val amount = mediaElem(29 mg);  val cost =  alibaba(0.14) }
  case object CuSO4       extends MediaComp { val n = "CuSO4 ";       val amount = mediaElem(3.2 mg); val cost =  alibaba(2.60) }
  case object Na2MoO42H2O extends MediaComp { val n = "Na2MoO4.2H2O"; val amount = mediaElem(4.8 mg); val cost = alibaba(12.00) }
  case object CoCl26H20   extends MediaComp { val n = "CoCl2.6H20";   val amount = mediaElem(4.7 mg); val cost =  alibaba(9.00) }
  case object MnCl24H2O   extends MediaComp { val n = "MnCl2.4H2O";   val amount = mediaElem(3.2 mg); val cost =  alibaba(1.50) }
  case object Biotin      extends MediaComp { val n = "Biotin";       val amount = mediaElem(0.6 mg); val cost =  alibaba(0.50) }
  case object CaPantothe  extends MediaComp { val n ="CaPantothenate";val amount = mediaElem(12 mg);  val cost = alibaba(20.00) }
  case object NicotinicAc extends MediaComp { val n = "NicotinicAcid";val amount = mediaElem(12 mg);  val cost = alibaba(15.00) }
  case object Myoinositol extends MediaComp { val n = "Myoinositol";  val amount = mediaElem(30 mg);  val cost =  alibaba(9.00) }
  case object ThiamineHCl extends MediaComp { val n = "ThiamineHCl";  val amount = mediaElem(12 mg);  val cost = alibaba(35.00) }
  case object PyroxidolHC extends MediaComp { val n = "PyroxidolHCl"; val amount = mediaElem(12 mg);  val cost = alibaba(20.00) }

  val allMediaComponents = List(KH2PO4, MgSO47H2O, NH42SO4, EDTA, FeSO47H2O, ZnSO47H2O, CaCl22H2O, CuSO4, Na2MoO42H2O, CoCl26H20, MnCl24H2O, Biotin, CaPantothe, NicotinicAc, Myoinositol, ThiamineHCl, PyroxidolHC)
  val unitCostOfMedia = allMediaComponents.map(x => x.amount * x.cost).reduce(_ + _)

  case object Ammonia     { val n = "Ammonia"; val cost = 1.00 }
  case object Glucose     { val n = "Glucose"; val cost = 0.32 }
  case object Antifoam    { val n = "Antifoam"; }

  /********************************************************************************************
  *  Sensible defaults and external caller 
  ********************************************************************************************/

  var strainTiter: Titer = DefaultTiter;
  var strainYield: Yield = DefaultYield;
  var fermRunTime: Time = DefaultFermRunTime;
  var brothMassPerBatch: Mass = DefaultBrothMassPerBatch
  var location: Location = GER;

  def getPerTonCost(yields: Yield, titers: Titer): USDPerTon = {
    strainTiter = titers
    strainYield = yields
    RentalModelCost()
  }

  /********************************************************************************************
  *  Consumptions and cost per batch 
  ********************************************************************************************/
  def workingVolume: Volume = vesselSize * pcOfVesselUsed 
  def finalByInitialVol: Ratio = brothMassPerBatch / VolumeToMass(workingVolume)
  def literDaysPerBatch = (fermRunTime, vesselSize)
  def productPerBatch: Mass = brothMassPerBatch * strainTiter.value
  def glcConsumedPerBatch: Mass = productPerBatch / strainYield.value
  def glcBatchedPerBatch: Mass = 0 grams // Glc in media = 0kgs: TODO: check if this is correct
  def glcFedPerBatch: Mass = glcConsumedPerBatch - glcBatchedPerBatch
  def ammoniaUsedPerBatch: Mass = ammoniaPerGlucose * glcConsumedPerBatch

  def mediaPerBatch: Money = brothMassPerBatch * unitCostOfMedia
  def glcPerBatch: USDPerBatch = Glucose.cost * glcFedPerBatch
  def ammoniaPerBatch: USDPerBatch = Ammonia.cost * ammoniaUsedPerBatch 
  def consumablesPerBatch: USDPerBatch = mediaPerBatch + glcPerBatch + ammoniaPerBatch

  /********************************************************************************************
  *  Rental Model  
  ********************************************************************************************/
  def RentalModelCost(): USDPerTon = {
    val rentPerBatch = literDaysPerBatch * CentsToUSD(location.rentalRate)

    // brothvol = working vol + num_draws * (working vol)/2 => num_draws = 2(brothvol/working - 1)
    def numDraws: Ratio = 2 * (finalByInitialVol - 1)
    // final volume depends on num of draws, and there cannot be more than one draw a day (can there?)
    // which puts an upper bound on the final volume you can extract from a fermentation over x days
    // Do a sanity check that we did not get a input "final volume" which is insane compared to runtime
    assert(numDraws <= fermRunTime)

    val costPerBatch: USDPerBatch = consumablesPerBatch + rentPerBatch
    val costPerTon: USDPerTon = costPerBatch / KgToTon(productPerBatch)
    costPerTon
  }

  /********************************************************************************************
  *  Bottom Up Model 
  ********************************************************************************************/
  def BottomUpModelCost(): USDPerTon = {
    val electrical: USDPerBatch = 0
    val cooling: USDPerBatch = 0
    val steam: USDPerBatch = 0
    val labor: USDPerBatch = 0
    val depreciation: USDPerBatch = 0

    val costPerBatch: USDPerBatch = consumablesPerBatch + electrical + cooling + steam + labor + depreciation
    val costPerTon: USDPerTon = costPerBatch / KgToTon(productPerBatch)
    costPerTon
  }

}
