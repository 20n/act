package act.installer.bing

import squants.time.Time
import squants.time.Days

import squants.mass.Mass
import squants.mass.Kilograms
import squants.space.Volume
import squants.space.Litres
import squants.Dimensionless

import squants.market.Money
import squants.market.USD
import squants.market.Price
import squants.Ratio
import squants.LikeRatio
import squants.thermal.Temperature
import squants.Quantity
import squants.mass.Moles
import squants.mass.Density
import squants.mass.ChemicalAmount

// these provide implicit converters such as
// (XX dollars) (XX days) (XX kg) (XX liters) etc.
import squants.time.TimeConversions.TimeConversions
import squants.mass.MassConversions.MassConversions
import squants.space.VolumeConversions.VolumeConversions
import squants.DimensionlessConversions.DimensionlessConversions
import squants.market.MoneyConversions.MoneyConversions
import squants.thermal.TemperatureConversions.TemperatureConversions
import squants.energy.PowerConversions.PowerConversions
import squants.mass.ChemicalAmountConversions.ChemicalAmountConversions

// Getting (XX millions) i.e., a dimensionless multiplier has
// different exporting consideration than the other conversions above
// The below _ imports `lazy val millions` from https://github.com/garyKeorkunian/squants/blob/master/shared/src/main/scala/squants/Dimensionless.scala#L111
// TODO is there a better way to import?
import squants.DimensionlessConversions._

import scala.math.sinh
import java.lang.UnsupportedOperationException
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.logging.log4j.LogManager

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

  def getPerTonCost(yield_is: Yield, titer_is: Titer, mode: Defaults.OperationMode): Price[Mass] = {
    strainTiter = titer_is
    strainYield = yield_is
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
    // TODO fill out the cost model for Build Your Own Plant
    throw new UnsupportedOperationException()

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
  val defaultPricePerTon: Money = USD(5547) // current price of acetaminophen, from market report in NAS/shared-data/Reports and Documents/Market Reports/Global Acetaminophen Industry Report 2015 (02_15_2016).pdf

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

  def getInvestmentRequired(yield_is: Yield, titer_is: Titer): (Money, Time) = {
    strainTiter = titer_is
    strainYield = yield_is
    getInvestment()
  }

  def asymptoticCurve(normYield: Double, normTiter: Double): Double = {
    // a resonable approximation would be 0.02\sinh(8x-3.9)+0.5 (between (0,0) and (1,1)
    def curve(x: Double) = { 0.02 * sinh(8 * x - 3.9) + 0.5 }

    // TODO: return a convolved outcome based on titer AND yield
    // From observations, we know titer is the predominant cost,
    // so for now we compute a return curve on titer
    curve(normTiter)
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

  // Calculate the Net Present Value: https://en.wikipedia.org/wiki/Net_present_value
  // An estimate of how much this money would be worth over a period
  def getNPV(invested: Money, profits: List[Money]): Money = {
    // TODO: Change the NPV calculation to use Danielle's model
    // NAS/shared-data/Danielle/Final Docs/Other Stuff/Molecule NPV.xlsx

    def discountfn(yrProfit: (Money, Int)) = yrProfit._1 / math.pow(1 + rate.value, 1.0 * yrProfit._2)
    val discountedProfits = profits.zip(1 until profits.length).map(discountfn)

    discountedProfits.reduce(_ + _)
  }

  def getROI(): (Money, Dimensionless) = getROI(Defaults.defaultYield, Defaults.defaultTiter, Defaults.defaultPricePerTon, Defaults.defaultOperationMode)

  def getROI(yield_is: Yield, titer_is: Titer, marketPricePerTon: Money, mode: Defaults.OperationMode): (Money, Dimensionless) = {
    strainTiter = titer_is
    strainYield = yield_is
    productPrice = marketPricePerTon

    val productionPrice: Price[Mass] = new CostModel().getPerTonCost(yield_is, titer_is, mode)
    val productionPricePerTon: Money = productionPrice * (1 tonnes)
    val profitPerTon: Money = marketPricePerTon - productionPricePerTon
    val eventualProfit: Money = profitPerTon * BigDecimal(volume.value)
    val startingProfit: Money = profitPerTon * BigDecimal(startingVolume.value)
    // not counting the start and end year, we calculate what the
    // step in Money needs to be at every intermediate year.
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

  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  val HELP_MESSAGE = ""
  HELP_FORMATTER.setWidth(100)
  private val logger = LogManager.getLogger(getClass.getName)

  def main(args: Array[String]) {
    val cl = parseCommandLineOptions(args)

    // market price USD/ton of product
    val p = USD(cl.getOptionValue(OPTION_MARKET_PRICE).toDouble)
    val mode = cl.getOptionValue(OPTION_MODE) match {
      case "CMOS" => Defaults.CMOS
      case _ => Defaults.BYOP
    }
    val outformat = cl.getOptionValue(OPTION_OUTFORMAT) match {
      case "Readable" => OutHuman
      case _ => OutTSV
    }

    // Print the header
    val hdr = outformat match {
      case OutHuman => List("Yield", "Titer", "Investment", "COGS", "ROI").mkString("\t")
      case OutTSV => List("Yield(%)", "Titer(g/L)", "Invest($$M)", "Invest(Yr)", "COGS($$/T)", "Sell Price($$/T)", "NPV($$M)", "ROI(%)").mkString("\t")
    }
    println(hdr)

    for (titerv <- 1.0 to Defaults.maxTiter.gPerL by 20.0) {
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
            println(f"$yieldPc%2.2f\t$titerGPerL%2.2f\t$investMillions%2.2f\t$investYears%.2f\t$cogsForTon%.2f\t$priceForTon%.2f\t$npv%.2f\t$roiPc%.2f")
          }
        }
      }
    }
  }

  private val OPTION_MARKET_PRICE = "p"
  private val OPTION_MODE = "m"
  private val OPTION_OUTFORMAT = "f"

  def parseCommandLineOptions(args: Array[String]): CommandLine = {
    val opts = getCommandLineOptions

    // Parse command line options
    var cl: Option[CommandLine] = None
    try {
      val parser = new DefaultParser()
      cl = Option(parser.parse(opts, args))
    } catch {
      case e: ParseException =>
        logger.error(s"Argument parsing failed: ${e.getMessage}\n")
        exitWithHelp(opts)
    }

    if (cl.isEmpty) {
      logger.error("Detected that command line parser failed to be constructed.")
      exitWithHelp(opts)
    }

    if (cl.get.hasOption("help")) exitWithHelp(opts)

    logger.info("Finished processing command line information")
    cl.get
  }

  def getCommandLineOptions: Options = {

    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_MARKET_PRICE).
        required(true).
        hasArg.
        longOpt("market-price").
        desc("The EC number to query against in format of X.X.X.X, " +
          "such as the value 6.1.1 will match 6.1.1.1 as well as 6.1.1.2"),

      CliOption.builder(OPTION_MODE).
        required(true).
        hasArg.
        longOpt("mode").
        desc("The operation mode, CMO-based or Build Your Own Plant, CMOS or BYOP, respectively to derive the cost model for."),

      CliOption.builder(OPTION_OUTFORMAT).
        required(true).
        hasArg.
        longOpt("output-format").
        desc("The output format, one of Readable or TSV "),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }
}
