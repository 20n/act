package com.act.reachables

import java.lang.Long
import java.util

import com.fasterxml.jackson.annotation.{JsonCreator, JsonIgnore, JsonProperty}
import com.mongodb.{DB, MongoClient, ServerAddress}
import org.apache.commons.codec.digest.DigestUtils
import org.mongojack.JacksonDBCollection

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable




// Default host. If running on a laptop, please set a SSH bridge to access speakeasy

object Cascade extends Falls {
  val mongoClient: MongoClient = new MongoClient(new ServerAddress("localhost", 27017))
  val db: DB = mongoClient.getDB("wiki_reachables")
  val collectionName: String = "pathways"

  private val pathwayCollection: JacksonDBCollection[ReactionPath, String] = JacksonDBCollection.wrap(db.getCollection(collectionName), classOf[ReactionPath], classOf[String])

  case class SubProductPair(substrates: List[Long], products: List[Long])

  val nodeMerger: mutable.HashMap[SubProductPair, Node] = new mutable.HashMap()

  // depth upto which to generate cascade data
  var max_cascade_depth = GlobalParams.MAX_CASCADE_DEPTH

  // the best precursor reaction
  var cache_bestpre_rxn = Map[Long, Set[ReachRxn]]()

  // the cache of the cascade if it has been
  // previously computed
  var cache_nw = mutable.HashMap[Long, Network]()

  // We only pick rxns that lead monotonically backwards in the tree.
  // This is conservative to avoid cycles (we could be optimistic and jump
  // fwd in the tree, if the rxn is really good, but we risk infinite loops then)

  def pre_rxns(m: Long, higherInTree: Boolean = true): Map[SubProductPair, List[ReachRxn]] = {
//    if (cache_bestpre_rxn contains m) cache_bestpre_rxn(m) else {

    // incoming unreachable rxns ignored
    val upReach = upR(m).filter(_.isreachable)

    // we dont want to use reactions that dont have any substrates (most likely bad data)
    val upNonTrivial = upReach.filter(has_substrates)

    // limit the # of up reactions to output to MAX_CASCADE_UPFANOUT
    // compute all substrates "s" of all rxnsups (upto 10 of them)
    val groupedSubProduct: Map[SubProductPair, List[ReachRxn]] = upNonTrivial.toList
      .map(rxn => (SubProductPair(rxn.substrates.toList.sorted, List(m)), rxn)).
      groupBy(_._1).
      mapValues(_.map(_._2))

    val sortedByEvidence = groupedSubProduct.entrySet().toList.sortBy(-_.getValue.length)

    if (sortedByEvidence.length >= 2 && sortedByEvidence.head.getValue.length > sortedByEvidence(1).getValue.length){

      val mostEvidenceFor = sortedByEvidence.head

      val passing: List[ReachRxn] = if (higherInTree) {
        sortedByEvidence.tail.flatMap(_.getValue).filter(higher_in_tree(m, _))
      } else {
        sortedByEvidence.tail.flatMap(_.getValue)
      }

      val passingGrouped: Map[SubProductPair, List[ReachRxn]] = passing
        .map(rxn => (SubProductPair(rxn.substrates.toList.sorted, List(m)), rxn)).
        groupBy(_._1).
        mapValues(_.map(_._2))

      passingGrouped + (mostEvidenceFor.getKey -> mostEvidenceFor.getValue)
    } else {
      val passing: List[ReachRxn] = if (higherInTree) {
        sortedByEvidence.flatMap(_.getValue).filter(higher_in_tree(m, _))
      } else {
        sortedByEvidence.flatMap(_.getValue)
      }

      val passingGrouped: Map[SubProductPair, List[ReachRxn]] = passing
        .map(rxn => (SubProductPair(rxn.substrates.toList.sorted, List(m)), rxn)).
        groupBy(_._1).
        mapValues(_.map(_._2))

      passingGrouped
    }
  }

  val rxnIdShift = 4000000000l

  val pattern = """:\[([\s\d\,]*)\]""".r

  // dot does not like - in identifiers. Replace those with underscores
  def rxn_node_ident(id: Long) = rxnIdShift + id
  def mol_node_ident(id: Long) = id

  def rxn_node_tooltip_string(id: Long) = {
    ReachRxnDescs.rxnEasyDesc(id) match {
      case None => "ID:" + id + " not in DB"
      // GraphViz chokes on "[" and "]". Replace these with "{" and "}"
      case Some(desc) => pattern.replaceAllIn(desc, m => s":{${m.group(1)}}")

    }
  }
  def rxn_node_label_string(id: Long) = {
    ReachRxnDescs.rxnECNumber(id) match {
      case None => "ID:" + id + " not in DB"
      case Some(ecnum) => ecnum
    }
  }
  def rxn_node_url_string(id: Long) = {
    ReachRxnDescs.rxnECNumber(id) match {
      case None => "ID:" + id + " not in DB"
      case Some(ecnum) => "http://brenda-enzymes.org/enzyme.php?ecno=" + ecnum
    }
  }

  def rxn_node(ids: List[Long], unique: SubProductPair): Node = {
    val labelBuilder = new StringBuilder
    val labelSet: Set[String] = ids.map(id => rxn_node_label_string(id)).toSet
    labelSet.foreach(id => labelBuilder.append("&&&&").append(id))

    // Get sorted list of organisms
    val organisms = ids.flatMap(id => ReachRxnDescs.rxnOrganismNames(id).get).sorted(Ordering[String].reverse)

    if (nodeMerger.contains(unique)){
      val previouslyCreatedNode = nodeMerger(unique)
      val ident = previouslyCreatedNode.id

      Node.getAttribute(ident, "reaction_ids").asInstanceOf[util.HashSet[Long]].add(ident)
      val s: util.HashSet[Long] = (Node.getAttribute(ident, "reaction_ids").asInstanceOf[util.HashSet[Long]])
      s.addAll(ids)

      Node.setAttribute(ident, "reaction_ids", s)
       Node.setAttribute(ident, "reaction_count", s.size())

      Node.setAttribute(ident, "label_string", Node.getAttribute(ident, "label_string") + labelBuilder.toString())

      val addedOrganisms = Node.getAttribute(ident, "organisms").asInstanceOf[util.ArrayList[String]].toList ::: organisms
      Node.setAttribute(ident, "organisms",  new util.ArrayList(addedOrganisms.sorted(Ordering[String].reverse).asJava))
      return nodeMerger(unique)
    }

    val ident = rxn_node_ident(ids.head)
    val node = Node.get(ident, true)
    Node.setAttribute(ident, "isrxn", "true")
    val ridSet = new util.HashSet[Long]()
    ridSet.addAll(ids)
    Node.setAttribute(ident, "reaction_ids", ridSet)
    Node.setAttribute(ident, "reaction_count", ridSet.size())
    Node.setAttribute(ident, "label_string", labelBuilder.toString())
    Node.setAttribute(ident, "tooltip_string", rxn_node_tooltip_string(ids.head))
    Node.setAttribute(ident, "url_string", rxn_node_url_string(ids.head))
    Node.setAttribute(ident, "organisms", new util.ArrayList(organisms.asJava))
    nodeMerger.put(unique, node)
    node
  }

//  def rxn_node(id: Long, unique: SubProductPair): Node = {
//    if (nodeMerger.contains(unique)){
//      val previouslyCreatedNode = nodeMerger(unique)
//      val ident = previouslyCreatedNode.id
//      val newCount: Int = Node.getAttribute(ident, "reaction_count").asInstanceOf[Int] + 1
//      Node.setAttribute(ident, "reaction_count", newCount)
//      Node.setAttribute(ident, "reaction_ids", Node.getAttribute(ident, "reaction_ids") + s"_$id")
//      Node.setAttribute(ident, "label_string", Node.getAttribute(ident, "label_string") + "&&&&" + rxn_node_label_string(id))
////      Node.setAttribute(ident, "tooltip_string", Node.getAttribute(ident, "tooltip_string") + "&&&&" + rxn_node_tooltip_string(id))
//      return nodeMerger(unique)
//    }
//
//    if (id > GlobalParams.FAKE_RXN_ID) {
//      val num_omitted = id - GlobalParams.FAKE_RXN_ID
//      val node = Node.get(id, true)
//      Node.setAttribute(id, "isrxn", "true")
//      Node.setAttribute(id, "reaction_count", 1)
//      Node.setAttribute(id, "reaction_ids", s"$id")
//      Node.setAttribute(id, "label_string", num_omitted + " more")
//      Node.setAttribute(id, "tooltip_string", num_omitted + " more")
//      Node.setAttribute(id, "url_string", "")
//      nodeMerger.put(unique, node)
//      node
//    } else {
//      val ident = rxn_node_ident(id)
//      val node = Node.get(ident, true)
//      Node.setAttribute(ident, "isrxn", "true")
//      Node.setAttribute(id, "reaction_count", 1)
//      Node.setAttribute(ident, "reaction_ids", s"$id")
//      Node.setAttribute(ident, "label_string", rxn_node_label_string(id))
//      Node.setAttribute(ident, "tooltip_string", rxn_node_tooltip_string(id))
//      Node.setAttribute(ident, "url_string", rxn_node_url_string(id))
//      nodeMerger.put(unique, node)
//      node
//    }
//  }
  def mol_node(id: Long) = {
    val ident = mol_node_ident(id)
    val node = Node.get(ident, true)
    val inchi = ActData.instance.chemId2Inchis.get(id)
    Node.setAttribute(ident, "isrxn", "false")
    Node.setAttribute(ident, "label_string", fixed_sz_svg_img(id)) // do not quote the <<TABLE>>
    Node.setAttribute(ident, "tooltip_string", quote(inchi))
    Node.setAttribute(ident, "url_string", quote(mol_node_url_string(inchi)))
    node
  }

  def fixed_sz_svg_img(id: Long): String = {
    // From: http://www.graphviz.org/content/images-nodes-label-below
    // Put DOT label like so:
    // <<TABLE border="0" cellborder="0"> <TR><TD width="60" height="50" fixedsize="true">
    // <IMG SRC="20n.png" scale="true"/></TD><td><font point-size="10">protein2ppw</font></td></TR></TABLE>>

    val inchi = ActData.instance().chemId2Inchis.get(id)
    // Generate md5 hash for inchi
    val md5 = DigestUtils.md5Hex(if (inchi == null) "" else inchi)
    // Format the rendering filename
    val renderingFilename = String.format("molecule-%s.png", md5)

    val readableName = ActData.instance.chemId2ReadableName.get(id)
    val myName = if (readableName.startsWith("InChI")){
      readableName.split("/")(1)
    } else {
      readableName
    }

    // Construct the string
    val name = s"""<td><font point-size=\"12\">${
      myName
    }</font></td>"""
    val img = s"""<TD width=\"120\" height=\"100\" fixedsize=\"true\"><IMG SRC=\"$renderingFilename\" scale=\"true\"/></TD>"""
    s"""<<TABLE border=\"0\" cellborder=\"0\"><TR>$img$name</TR></TABLE>>"""
  }

  def mol_node_url_string(inchi: String) = {
    if (inchi == null) {
      "no inchi"
    } else {
      "http://www.chemspider.com/Search.aspx?q=" + java.net.URLEncoder.encode(inchi, "utf-8")
    }
  }
  def quote(str: String) = {
    "\"" + str + "\""
  }

  def create_edge(src: Node, dst: Node) = Edge.get(src, dst, true)

  def set_max_cascade_depth(depth: Integer) {
    max_cascade_depth = depth
  }

  def get_cascade(m: Long, depth: Int = 0, source: Option[Long] = None, seen: Set[Long] = Set()): Option[Network] = {
    if (source.isDefined && source.get == m) return None
    val network = new Network("cascade_" + m)

    network.addNode(mol_node(m), m)

    if (is_universal(m)) {
      // do nothing, base case
    } else {
      // We don't filter by higher in tree on the first iteration, so that all possible
      // reactions producing this product are shown on the graph.
      val groupedSubProduct = pre_rxns(m, higherInTree = depth != 0).toList
      
      var oneValid = false
      groupedSubProduct
        .filter(x => x._1.substrates.forall(x => !seen.contains(x)))
        .foreach({ case (subProduct, reactions) =>

        // Let's not show cofactor only reactions for now
        if (!subProduct.substrates.forall(cofactors.contains)) {
          val reactionsNode = rxn_node(reactions.map(r => Long.valueOf(r.rxnid)), subProduct)

          val subProductNetworks = subProduct.substrates.map(s => (s, get_cascade(s, depth + 1, Option(if (depth == 0) m else source.get), seen + m)))
          if (subProductNetworks.forall(_._2.isDefined)) {
            oneValid = true
            subProductNetworks.foreach(s => {
              network.addNode(reactionsNode, reactions.head.rxnid)

              subProduct.products.foreach(p => network.addEdge(create_edge(reactionsNode, mol_node(p))))

              network.mergeInto(s._2.get)

              // add edges of form "s" -> respective "r" nodeMapping
              network.addEdge(create_edge(mol_node(s._1), reactionsNode))
            })
          }
        }
      })

      if (!oneValid && depth > 0){
        return None
      }
    }


    Option(network)
  }

  def getAllPaths(network: Network, target: Long): Option[List[Path]] = {
    val sourceEdgesSet: util.Set[Edge] = network.getEdgesGoingInto(target)

    // If the target is a native then the only path is the node itself.
    var counter: Int = -1
    if(sourceEdgesSet == null) {
      if (network.nodes.isEmpty) {
        return None
      }
      return Option(List(new Path(List(network.nodes.toList.head))))
    }

    val sourceEdges = sourceEdgesSet.asScala.toList

    Option(sourceEdges.flatMap(e => {
      val path = getPath(network, e)
      if (path.isDefined) {
        counter = counter + 1
        Option(path.get.map(p => new Path(List(e.dst, e.src) ::: p.getPath)))
      } else {
        None
      }
    }).flatten)
  }

//  @tailrec
  def getPath(network: Network, edge: Edge, seenNodes: Set[Node] = Set()): Option[List[Path]] = {
    // Base case
    val reactionNode = edge.src

    // If reaction node has more than one edge we say that this isn't a viable path
    if (network.getEdgesGoingInto(reactionNode).size() > 1) return None

    val substrateNode = network.getEdgesGoingInto(reactionNode).head.src
    if (seenNodes.contains(substrateNode)) {
      return None
    }

//
//    // Is universal
    if (is_universal(substrateNode.id)) return Option(List(new Path(List(substrateNode))))
//
    val edgesGoingInto: List[Edge] = network.getEdgesGoingInto(substrateNode).toList

    // Get back a bunch of maybe paths
    val resultingPaths: List[Path] = edgesGoingInto.flatMap(x => {
      val grabPath = getPath(network, x, seenNodes + substrateNode)
      if (grabPath.isDefined) {
        Option(grabPath.get.map(p => new Path(List(substrateNode, reactionNode) ::: p.getPath)))
      } else {
        None
      }
    }).flatten

    if (resultingPaths.isEmpty){
      None
    } else {
      Option(resultingPaths)
    }
  }

  @JsonCreator
  class ReactionPath(@JsonProperty("path") path: util.ArrayList[Node], @JsonProperty("_id") id: String) {

    @JsonProperty("_id")
    def getId(): String = {
      id
    }

    def getTarget(): Long = {
      id.split("w")(0).toLong
    }

    def getRank(): Long = {
      id.split("w")(1).toLong
    }

    def getPath: util.ArrayList[Node] ={
      path
    }

    def getDegree(): Int = {
      getReactionCount(path.get(1))
    }

    def getReactionSum(): Int ={
      path.map(getReactionCount).sum
    }

    @JsonIgnore
    private def getReactionCount(node: Node): Int = {
      // Only reactions contribute
      if (Node.getAttribute(node.id, "isrxn").asInstanceOf[String].toBoolean) {
        node.getAttribute("reaction_count").asInstanceOf[Int]
      } else {
        0
      }
    }
  }

  class Path(path: List[Node]) {
    def getPath: List[Node] ={
      path
    }

    def getDegree(): Int = {
      getReactionCount(path.get(1))
    }

    def getReactionSum(): Int ={
      path.map(getReactionCount).sum
    }

    @JsonIgnore
    private def getReactionCount(node: Node): Int = {
      // Only reactions contribute
      if (Node.getAttribute(node.id, "isrxn").asInstanceOf[String].toBoolean) {
        node.getAttribute("reaction_count").asInstanceOf[Int]
      } else {
        0
      }
    }
  }
}

class Cascade(target: Long) {
  val t = target
  val nw = Cascade.get_cascade(t).get

  val viablePaths: Option[List[Cascade.Path]] = Cascade.getAllPaths(nw, t)

  val allPaths: List[Cascade.Path] = if (viablePaths.isDefined) {
    viablePaths.get.sortBy(p => (-p.getDegree(), -p.getReactionSum()))
  } else {
    List()
  }

  var c = -1
  allPaths.foreach(p => {
    c += 1
    Cascade.pathwayCollection.insert(new Cascade.ReactionPath(new util.ArrayList(p.getPath), s"${target}w$c"))
  })

  val allStringPaths: List[String] = allPaths.map(currentPath => {val allChemicalStrings: List[String] = currentPath.getPath.toList.flatMap(node => {
      Option(ActData.instance.chemId2ReadableName.get(node.id))
    })
    allChemicalStrings.mkString(", ")
  })


  def network() = nw

  def dot(): String = nw.toDOT

  def getPaths: List[String] = allStringPaths

}
