package act.installer.bing

import squants.time._
import squants.time.TimeConversions.TimeConversions

import squants.mass._
import squants.mass.MassConversions.MassConversions
import squants.space._
import squants.space.Volume._
import squants.space.VolumeConversions.VolumeConversions
import squants.Dimensionless
import squants.Dimensionless._
import squants.DimensionlessConversions._

import squants.market.Money
import squants.market.USD
import squants.market.Price
import squants.market.MoneyConversions._
import squants.Ratio
import squants.LikeRatio
import squants.thermal.Temperature
import squants.thermal.TemperatureConversions._
import squants.Quantity
import squants.energy._
import squants.energy.PowerConversions._
import squants.mass.Moles
import squants.mass.Density
import squants.mass.ChemicalAmount
import squants.mass.ChemicalAmountConversions._

import scala.math.sinh

case class Yield(base: Mass, counter: Mass) extends LikeRatio[Mass]
case class Titer(base: Mass, counter: Volume) extends Ratio[Mass, Volume] {
  def *(that: Volume): Mass = base * (that.value / counter.value)
  def /(that: Titer): Double = (base.value / that.base.value) * (that.counter.value / counter.value)
  def gPerL = this.convertToBase(1.0 liters).toGrams
}

class CostModel {

  type KiloWattPerMeterCubed = Double
  type KiloJoulePerMMol = Double
  type MMolPerLiterHour = Double

  /********************************************************************************************
  *  Unit Conversions 
  ********************************************************************************************/

  def waterDensity: Density = (1 kg) / (1 liters)
  def brothDensity: Density = waterDensity
  def VolumeToMass(x: Volume): Mass = brothDensity * x
  def MassToVolume(x: Mass): Volume = {
    val massInOneLiter: Mass = brothDensity * (1 liters)
    val litersToHoldInputMass = x / massInOneLiter
    Litres(litersToHoldInputMass)
  }

  /********************************************************************************************
  *  Constants 
  ********************************************************************************************/

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

  var strainTiter: Titer = Defaults.defaultTiter;
  var strainYield: Yield = Defaults.defaultYield;
  var operationMode: Defaults.OperationMode = Defaults.defaultOperationMode
  var fermRunTime: Time = defaultFermRunTime;
  var brothMassPerBatch: Mass = defaultBrothMassPerBatch
  var location: Location = GER;

  def getPerTonCost(y: Double, t: Double): Double = {
    val cost: Price[Mass] = getPerTonCost(Yield(y grams, 100 grams), Titer(t grams, 1 litres), Defaults.defaultOperationMode)
    cost.convertToBase(1 tonnes).value
  }

  def getPerTonCost(yields: Yield, titers: Titer, mode: Defaults.OperationMode): Price[Mass] = {
    strainTiter = titers
    strainYield = yields
    val fermCost = mode match {
      case Defaults.CMOS => costWithCMOs
      case Defaults.BYOP => costWithBYOPlant
    }
    val dspCost = mode match {
      case Defaults.CMOS => dspCostWithCMOs
      case Defaults.BYOP => dspCostWithBYOPlant
    }

    val cost = fermCost + dspCost
    cost
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
  *  Rental Model: Cost with CMOs
  ********************************************************************************************/
  def costWithCMOs(): Price[Mass] = {
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

  // Note: The TODOs that remain below need to be filled out from Tim Revak's models under
  // NAS/shared-data/Tim Revak/Cost Models/20n COG, v2 25JUL16.xlsx

  /********************************************************************************************
  *  Bottom Up Model: Cost with Build Your Own Plant
  ********************************************************************************************/
  def costWithBYOPlant(): Price[Mass] = {
    // TODO: fill out the cost model for Build Your Own Plant
    val electrical: Money = USD(0) 
    val cooling: Money = USD(0) 
    val steam: Money = USD(0) 
    val labor: Money = USD(0)
    val depreciation: Money = USD(0)

    val costPerBatch: Money = consumablesPerBatch + electrical + cooling + steam + labor + depreciation
    val costPerTon: Price[Mass] = costPerBatch / productPerBatch
    costPerTon
  }

  def dspCostWithCMOs(): Price[Mass] = {
    // TODO: fill out the cost model for CMO for dsp
    (2000 dollars) / (1 tonnes)
  }

  def dspCostWithBYOPlant(): Price[Mass] = {
    // TODO: fill out the cost model for Build Your Own Plant for dsp
    (2000 dollars) / (1 tonnes)
  }

}

object Defaults {
  // Default titers and yields are instances for Shikimate from review paper:
  // "Recombinant organisms for production of industrial products" 
  // --- http://www.ncbi.nlm.nih.gov/pmc/articles/PMC3026452
  val defaultYield: Yield = Yield(31.9 grams, 100 grams) // 31.9% g/g
  val maxYield: Yield = Yield(60 grams, 100 grams) // 60% g/g
  val defaultTiter: Titer = Titer(84 grams, 1 litres) // 84 g/L
  val maxTiter: Titer = Titer(200 grams, 1 liters) // 170g/L is probably the max that has ever been accomplished
  val defaultPricePerTon: Money = USD(5547) // current price of acetaminophen

  sealed abstract class OperationMode
  case object CMOS extends OperationMode
  case object BYOP extends OperationMode
  val defaultOperationMode: OperationMode = CMOS // BYOP
}

class InvestModel {

  var strainTiter: Titer = Defaults.defaultTiter;
  var strainYield: Yield = Defaults.defaultYield;

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

    // TODO: return a convolved outcome based on titer (x2) AND yield (x1)
    // From observations, we know titer is the predominant cost, so for now
    // we compute a return curve on titer (x2)
    curve(x2)
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
    val normYield = strainYield.ratio / Defaults.maxYield.ratio // gives us a number between [0,1]
    val normTiter = strainTiter / Defaults.maxTiter // gives us a number between [0,1]

    (cost(normYield, normTiter), time(normYield, normTiter))
  }
}

class ROIModel {

  var strainTiter: Titer = Defaults.defaultTiter;
  var strainYield: Yield = Defaults.defaultYield;
  var productPrice: Money = Defaults.defaultPricePerTon;

  val yearsToFullScale: Int = 3
  val volume: Mass = 1000 tonnes
  val startingVolume: Mass = 100 tonnes
  val rate = (10 / 100) percent

  def getNPV(invested: Money, profits: List[Money]): Money = {
    // TODO: Change the NPV calculation to use Danielle's model
    // NAS/shared-data/Danielle/Final Docs/Other Stuff/Molecule NPV.xlsx

    def discountfn(yrProfit: (Money, Int)) = yrProfit._1 / math.pow(1 + rate.value, 1.0 * yrProfit._2)
    val discountedProfits = profits.zip(1 until profits.length).map(discountfn)

    discountedProfits.reduce(_ + _)
  }

  def getROI(): (Money, Dimensionless) = getROI(Defaults.defaultYield, Defaults.defaultTiter, Defaults.defaultPricePerTon, Defaults.defaultOperationMode)

  def getROI(yields: Yield, titers: Titer, marketPricePerTon: Money, mode: Defaults.OperationMode): (Money, Dimensionless) = {
    strainTiter = titers
    strainYield = yields
    productPrice = marketPricePerTon

    val productionPrice: Price[Mass] = new CostModel().getPerTonCost(yields, titers, mode)
    val productionPricePerTon: Money = productionPrice * (1 tonnes)
    val profitPerTon: Money = marketPricePerTon - productionPricePerTon
    val eventualProfit: Money = profitPerTon * BigDecimal(volume.value)
    val startingProfit: Money = profitPerTon * BigDecimal(startingVolume.value)
    val step: Money = (eventualProfit - startingProfit) / (yearsToFullScale - 2)
    val stepNum = yearsToFullScale
    val profitRamp: List[Money] = (0 to stepNum - 1).toList.map(BigDecimal(_) * step + startingProfit)

    val investmentNeed: (Money, Time) = new InvestModel().getInvestment()

    val invested: Money = investmentNeed._1
    val npv = getNPV(invested, profitRamp)
    val gain: Money = profitRamp.reduce(_ + _)
    val roi: Dimensionless = ((gain - invested).value / invested.value) percent
    
    (npv, roi)
  }

}

object ExploreRange {
  val costmodel = new CostModel()
  val investmodel = new InvestModel()
  val roimodel = new ROIModel()

  sealed abstract class OutFormat
  case object OutHuman extends OutFormat
  case object OutTSV extends OutFormat

  def main(args: Array[String]) {
    if (args.length != 3) {
      printhelp
    } else {
      try {
        val p = USD(args(0).toDouble) // market price USD/ton of product
        val mode = args(1) match { case "CMOS" => Defaults.CMOS; case _ => Defaults.BYOP }
        val outformat = args(2) match { case "Readable" => OutHuman; case _ => OutTSV }

        // Print the header
        outformat match {
          case OutHuman => println(s"Yield\tTiter\tInvestment\tCOGS\tROI")
          case OutTSV => println(s"Yield(%)\tTiter(g/L)\tInvest($$M)\tInvest(Yr)\tCOGS($$/T)\tSell Price($$/T)\tNPV($$M)\tROI(%)")
        }

        val maxTite = 170.0 // Defaults.maxTiter.gPerL
        for (titerv <- 1.0 to maxTite by 20.0) {
          for (yieldv <- 1.0 to (100 * Defaults.maxYield.ratio) by 10.0) {
            val y = Yield(yieldv grams, 100 grams)
            val t = Titer(titerv grams, 1 litres)
            val investment: (Money, Time) = investmodel.getInvestmentRequired(y, t)
            val cogs: Price[Mass] = costmodel.getPerTonCost(y, t, mode)
            val roi: (Money, Dimensionless) = roimodel.getROI(y, t, p, mode)

            outformat match {
              case OutHuman => {
                println(s"$y $t $investment $cogs $roi")
              }
              case OutTSV => {
                val yieldPc = y.ratio * 100
                val titerGPerL = t.gPerL
                val investMillions = investment._1.value / 1e6
                val investYears = investment._2.value / 365
                val cogsForTon = cogs.convertToBase(1.0 tonnes).value
                val priceForTon = p.value
                val npv = roi._1.value / 1e6
                val roiPc = roi._2.value * 100
                println(f"$yieldPc%2.2f\t$titerGPerL%2.2f\t$investMillions%2.2f\t${investYears}%.2f\t$cogsForTon%.2f\t$priceForTon%.2f\t$npv%.2f\t$roiPc%.2f")
              }
            }
          }
        }
      } catch {
        case e: NumberFormatException => printhelp
      }
    }
  }

  def printhelp() {
    def hr() = println("*" * 80)
    hr
    println("Usage: ExploreRange <Current Market USD/ton of product> <CMOS | BYOP> <Readable | TSV>")
    hr
  }
}
