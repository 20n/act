import java.io.File

import DataSerializationJsonProtocol._
import com.act.biointerpretation.networkanalysis.GraphViz.PrecursorReportVisualizer
import com.act.biointerpretation.networkanalysis.{MetaboliteCorpus, NetworkEdge, NetworkNode}
import spray.json._

import scala.collection.JavaConversions._

object Test {
  def main(args: Array[String]) {
    val corpy = new MetaboliteCorpus()
    corpy.populateCorpusFromJsonFile(new File("/Volumes/shared-data/Michael/LowThresholdForRegression/dl.toIonMatchesFormulasNoMinusCholesterolManyProjection0.005.txt"))
    val network = corpy.getMetabolismNetwork

    // All reaction set directories used to construct the corpuses
    val wd1 = new File("/Volumes/shared-data/Michael/Humanprojection/cholesterol_stuffs/reverse_1")
    val wd2 = new File("/Volumes/shared-data/Michael/Humanprojection/cholesterol_stuffs/reverse_2")
    val wd3 = new File("/Volumes/shared-data/Michael/Humanprojection/cholesterol_stuffs/reverse_2_fwd")
    val wd4 = new File("/Volumes/shared-data/Michael/Humanprojection/cholesterol_stuffs/two_sub_fwd")
    //val wd5 = new File("/Volumes/shared-data/Michael/Humanprojection/cholesterol_stuffs/two_sub_n1")
    val files = wd1.listFiles().toList ::: wd2.listFiles().toList ::: wd3.listFiles().toList ::: wd4.listFiles().toList //::: wd5.listFiles().toList

    val myResults: List[InchiResult] = files.flatMap(f => scala.io.Source.fromFile(f).getLines().
      mkString.parseJson.convertTo[List[InchiResult]])

    println(myResults.length)



    println(network.getEdges.size())

    myResults.foreach(result => {
      val substrateNodes: List[NetworkNode] = result.substrate.flatMap(x => {
        val node = network.getNodeOption(x)
        if (node.isPresent) {
          Some(node.get())
        } else {
          None
        }
      })

      val productNodes: List[NetworkNode] = result.products.flatMap(x => {
        val node = network.getNodeOption(x)
        if (node.isPresent) {
          Some(node.get())
        } else {
          None
        }
      })

      if (substrateNodes.length == result.substrate.length && productNodes.length == result.products.length) {
        val edge = new NetworkEdge(result.substrate, result.products)
        edge.addProjectorName(result.ros)
        network.addEdge(edge)
      }
    })


    val cholesterol = "InChI=1S/C27H46O/c1-18(2)7-6-8-19(3)23-11-12-24-22-10-9-20-17-21(28)13-15-26(20,4)25(22)14-16-27(23,24)5/h9,18-19,21-25,28H,6-8,10-17H2,1-5H3/t19-,21+,22+,23-,24+,25+,26+,27-/m1/s1"
    val report = network.getDerivativeReport(network.getNode(cholesterol), 3)
    new PrecursorReportVisualizer().buildDotGraph(report).writeGraphToFile(new File(s"/Volumes/shared-data/Michael/Humanprojection/cholesterol_stuffs/graphs/${6}report.dot"))
//    network.getEdges.toList.flatMap(edge => edge.getSubstrates).distinct.foreach(x =>{
//      val report = network.getPrecursorReport(network.getNode(x), 4)
//      var count = 0
//      if (report.getLevelMap.values().toSet.size > 2) {
//        new PrecursorReportVisualizer().buildDotGraph(report).writeGraphToFile(new File(s"/Volumes/shared-data/Michael/Humanprojection/cholesterol_stuffs/graphs/${count}report.txt"))
//        count += 1
//      }
//    })
  }




  case class InchiResult(substrate: List[String], ros: String, products: List[String])
}
