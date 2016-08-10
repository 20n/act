package act.installer.bing

import squants.time._
import squants.time.TimeConversions.TimeConversions

import squants.mass._
import squants.mass.MassConversions.MassConversions
import squants.space._
import squants.space.VolumeConversions.VolumeConversions
import squants.Dimensionless
import squants.Dimensionless._
import squants.DimensionlessConversions._

import squants.market.Money
import squants.market.USD
import squants.market.Price
import squants.Ratio
import squants.LikeRatio
import squants.thermal.Temperature
import squants.thermal.TemperatureConversions._
import squants.Quantity

import scala.math.sinh

case class Yield(base: Mass, counter: Mass) extends LikeRatio[Mass]
case class Titer(base: Mass, counter: Volume) extends Ratio[Mass, Volume] {
  def *(that: Volume): Mass = base * (that.value / counter.value)
  def /(that: Titer): Double = (base.value / that.base.value) * (that.counter.value / counter.value)
}

class CostModel(modelName: String) {

  type KiloWattPerMeterCubed = Double
  type KiloJoulePerMMol = Double
  type MMolPerLiterHour = Double

  /********************************************************************************************
  *  Unit Conversions 
  ********************************************************************************************/

  def VolumeToMass(x: Volume): Mass = Kilograms((x in Litres).value)
  def MassToVolume(x: Mass): Volume = Litres((x in Kilograms).value)

  /********************************************************************************************
  *  Constants 
  ********************************************************************************************/

  // Default titers and yields are instances for Shikimate from review paper:
  // "Recombinant organisms for production of industrial products" 
  // --- http://www.ncbi.nlm.nih.gov/pmc/articles/PMC3026452
  val defaultYield: Yield = Yield(31.9 grams, 100 grams) // 31.9% g/g
  val defaultTiter: Titer = Titer(84.0 grams, 1 litres) // 84 g/L
  val defaultFermRunTime: Time = 10 days
  val defaultBrothMassPerBatch: Mass = VolumeToMass(360 cubicMeters)

  def literDay(v: Volume, t: Time): Double = {
    v.toLitres * t.toDays
  }
  sealed trait Location { def name: String; def rentalRate: Money }
  def cmoRate(centsPerLiterDay: Double): Money = { USD(centsPerLiterDay / 100.00) }
  // See email thread "Model" between Saurabh, Jeremiah, Tim Revak for cost quotes from various places
  case object GER extends Location { val name = "Germany"; val rentalRate = cmoRate(5.0) }
  case object ITL extends Location { val name = "Italy"; val rentalRate = cmoRate(8.0) }
  case object IND extends Location { val name = "India"; val rentalRate = cmoRate(10.0) }
  case object CHN extends Location { val name = "China"; val rentalRate = cmoRate(5.3) }
  case object MID extends Location { val name = "Midwest"; val rentalRate = cmoRate(8.3) }
  case object MEX extends Location { val name = "Mexico"; val rentalRate = cmoRate(8.3) }

  /************************************ Fermentation ****************************************/
  // Productivity and Titer
  val vesselSize: Volume = 200 cubicMeters
  val pcOfVesselUsed: Dimensionless = (90.0 / 100.0) percent
  val operationalTimePerYear: Time = 350 days // all plants have two week downtime, so 365-14 days
  val fermTemp: Temperature = 25 C
  val hoursForCIP: Time = 12 hours // Clean In Place
  val finalDryCellWeight: Ratio[Mass, Mass] = mediaElem(170 g)
  val fermenterAspect: Double = 3.0 // height/diameter
  // Compressor Power
  val airFlow: Double = 2.0 
  // Agitator Power
  val agitationRate: KiloWattPerMeterCubed = 0.75
  // Microbial Heat
  val oxygenUptakeRate: MMolPerLiterHour = 120.0 // OUR in mmol O2/L/hr
  val microbialHeatGen: KiloJoulePerMMol = 0.52 // kJ/mmol O2
  // Ammonia Use
  val ammoniaPerGlucose: Dimensionless = (6.0 / 100.0) percent
  // Steam Use
  val initialTemp: Temperature = 20 C
  val sterilizedTemp: Temperature = 121 C

  // Labor
  val numOfEmployees: Integer = 14
  // Capital
  val DepreciationLife: Time = (15 * 365) days

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

  case object Ammonia     { val n = "Ammonia"; val cost = alibaba(1.00) }
  case object Glucose     { val n = "Glucose"; val cost = alibaba(0.32) }
  case object Antifoam    { val n = "Antifoam"; }

  /********************************************************************************************
  *  Sensible defaults and external caller 
  ********************************************************************************************/

  var strainTiter: Titer = defaultTiter;
  var strainYield: Yield = defaultYield;
  var fermRunTime: Time = defaultFermRunTime;
  var brothMassPerBatch: Mass = defaultBrothMassPerBatch
  var location: Location = GER;

  def getPerTonCost(y: Double, t: Double): Double = {
    val cost: Price[Mass] = getPerTonCost(Yield(y grams, 100 grams), Titer(t grams, 1 litres))
    (cost * (1 tonnes)).value
  }

  def getPerTonCost(yields: Yield, titers: Titer): Price[Mass] = {
    strainTiter = titers
    strainYield = yields
    RentalModelCost()
  }

  /********************************************************************************************
  *  Consumptions and cost per batch 
  ********************************************************************************************/
  def workingVolume: Volume = vesselSize * pcOfVesselUsed.value
  def finalByInitialVol: Double = brothMassPerBatch.value / VolumeToMass(workingVolume).value
  def literDaysPerBatch = literDay(vesselSize, fermRunTime)
  def productPerBatch: Mass = strainTiter * MassToVolume(brothMassPerBatch)
  def glcConsumedPerBatch: Mass = strainYield.inverseRatio * productPerBatch
  def glcBatchedPerBatch: Mass = 0 grams // Glc in media = 0kgs: TODO: check if this is correct
  def glcFedPerBatch: Mass = glcConsumedPerBatch - glcBatchedPerBatch
  def ammoniaUsedPerBatch: Mass = glcConsumedPerBatch * ammoniaPerGlucose.value

  def mediaPerBatch: Money = unitCostOfMedia * brothMassPerBatch
  def glcPerBatch: Money = Glucose.cost * glcFedPerBatch
  def ammoniaPerBatch: Money = Ammonia.cost * ammoniaUsedPerBatch 
  def consumablesPerBatch: Money = mediaPerBatch + glcPerBatch + ammoniaPerBatch

  /********************************************************************************************
  *  Rental Model  
  ********************************************************************************************/
  def RentalModelCost(): Price[Mass] = {
    val rentPerBatch = location.rentalRate * literDaysPerBatch

    // brothvol = working vol + num_draws * (working vol)/2 => num_draws = 2(brothvol/working - 1)
    def numDraws: Double = 2 * (finalByInitialVol - 1)
    // final volume depends on num of draws, and there cannot be more than one draw a day (can there?)
    // which puts an upper bound on the final volume you can extract from a fermentation over x days
    // Do a sanity check that we did not get a input "final volume" which is insane compared to runtime
    assert(numDraws <= (fermRunTime in Days).value)

    val costPerBatch: Money = consumablesPerBatch + rentPerBatch
    val costPerTon: Price[Mass] = costPerBatch / productPerBatch
    costPerTon
  }

  /********************************************************************************************
  *  Bottom Up Model 
  ********************************************************************************************/
  def BottomUpModelCost(): Price[Mass] = {
    val electrical: Money = USD(0) 
    val cooling: Money = USD(0) 
    val steam: Money = USD(0) 
    val labor: Money = USD(0)
    val depreciation: Money = USD(0)

    val costPerBatch: Money = consumablesPerBatch + electrical + cooling + steam + labor + depreciation
    val costPerTon: Price[Mass] = costPerBatch / productPerBatch
    costPerTon
  }

}

class InvestModel {
  val defaultYield: Yield = Yield(31.9 grams, 100 grams) // 31.9% g/g
  val maxYield: Yield = Yield(60.0 grams, 100.0 grams) // 60% g/g
  val defaultTiter: Titer = Titer(84.0 grams, 1 litres) // 84 g/L
  val maxTiter: Titer = Titer(200.0 grams, 1 liters) // 170g/L is probably the max that has ever been accomplished

  var strainTiter: Titer = defaultTiter;
  var strainYield: Yield = defaultYield;

  val maxProjectTime: Time = (365 * 10) days
  val maxProjectInvestment: Money = USD(20 million)

  def getInvestmentRequired(yields: Yield, titers: Titer): (Money, Time) = {
    strainTiter = titers
    strainYield = yields
    getInvestment()
  }

  def asymptoticCurve(x1: Double, x2: Double): Double = {
    // a resonable approximation would be 0.02\sinh(8x-3.9)+0.5 (between (0,0) and (1,1)
    def curve(x: Double) = { 0.02 * sinh(8 * x - 3.9) + 0.5 }

    curve(x2) // for now ignore x1 (yield)
  }

  def cost(normYield: Double, normTiter: Double): Money = {
    maxProjectInvestment * asymptoticCurve(normYield, normTiter)
  }

  def time(normYield: Double, normTiter: Double): Time = {
    maxProjectTime * asymptoticCurve(normYield, normTiter)
  }

  def getInvestment(): (Money, Time) = {
    // want to return the inverse shape of (1+\tanh(4x-2)) / 2 (has a good asymptotic form between (0,0) to (1,1)
    // a resonable approximation would be 0.02\sinh(8x-3.9)+0.5 (between (0,0) and (1,1)
    val normYield = strainYield.ratio / maxYield.ratio // gives us a number between [0,1]
    val normTiter = strainTiter / maxTiter // gives us a number between [0,1]

    (cost(normYield, normTiter), time(normYield, normTiter))
  }
}

class ROIModel
