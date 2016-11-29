package com.act.reachables

import java.lang.Long

import scala.collection.JavaConversions._
import scala.collection.mutable


object Cascade extends Falls {

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

  def pre_rxns(m: Long, higherInTree: Boolean = true): Set[ReachRxn] = {
//    if (cache_bestpre_rxn contains m) cache_bestpre_rxn(m) else {

    // incoming unreachable rxns ignored
    val upReach = upR(m).filter(_.isreachable)

    // we dont want to use reactions that dont have any substrates (most likely bad data)
    val upNonTrivial = upReach.filter(has_substrates)

    // to avoid circular paths, we require the precuror rxn to go towards natives
    val up = if (higherInTree) {
      upNonTrivial.filter(higher_in_tree(m, _))
    } else {
      upNonTrivial
    }

    // add to cache
//    cache_bestpre_rxn = cache_bestpre_rxn + (m -> up)

    // onwards, and upwards!
    up
  }

  val rxnIdShift = 4000000000l
  // dot does not like - in identifiers. Replace those with underscores
  def rxn_node_ident(id: Long) = rxnIdShift + id
  def mol_node_ident(id: Long) = id

  def rxn_node_tooltip_string(id: Long) = {
    ReachRxnDescs.rxnEasyDesc(id) match {
      case None => "ID:" + id + " not in DB"
      case Some(desc) => desc
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

    if (nodeMerger.contains(unique)){
      val previouslyCreatedNode = nodeMerger(unique)
      val ident = previouslyCreatedNode.id
      val newCount: Int = Node.getAttribute(ident, "reaction_count").asInstanceOf[Int] + ids.length
      Node.setAttribute(ident, "reaction_count", newCount)
      Node.setAttribute(ident, "reaction_ids", Node.getAttribute(ident, "reaction_ids") + s"_$ident")
      Node.setAttribute(ident, "label_string", Node.getAttribute(ident, "label_string") + labelBuilder.toString())
      return nodeMerger(unique)
    }

    val ident = rxn_node_ident(ids.head)
    val node = Node.get(ident, true)
    Node.setAttribute(ident, "isrxn", "true")
    Node.setAttribute(ident, "reaction_count", ids.length)
    Node.setAttribute(ident, "reaction_ids", s"$ident")
    Node.setAttribute(ident, "label_string", labelBuilder.toString())
    Node.setAttribute(ident, "tooltip_string", rxn_node_tooltip_string(ids.head))
    Node.setAttribute(ident, "url_string", rxn_node_url_string(ids.head))
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
    val imgfile = "img" + id + ".svg"

    // return the constructed string
    "<<TABLE border=\"0\" cellborder=\"0\"> " +
      "<TR><TD width=\"120\" height=\"100\" fixedsize=\"true\"><IMG SRC=\"" +
      imgfile +
      "\" scale=\"true\"/></TD><td><font point-size=\"12\">" +
      ActData.instance.chemId2ReadableName.get(id) +
      "</font></td></TR></TABLE>>"
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

  def get_cascade(m: Long, depth: Int, source: Option[Long] = None): Option[Network] = {
    if (source.isDefined && source.get == m) return None
    val network = new Network("cascade_" + m)

    network.addNode(mol_node(m), m)

    if (is_universal(m)) {
      // do nothing, base case
    } else {
      // We don't filter by higher in tree on the first iteration, so that all possible
      // reactions producing this product are shown on the graph.
      val rxnsup: List[ReachRxn] = pre_rxns(m, higherInTree = depth != 0).toList

      // limit the # of up reactions to output to MAX_CASCADE_UPFANOUT
      // compute all substrates "s" of all rxnsups (upto 10 of them)
      val groupedSubProduct: Map[SubProductPair, List[ReachRxn]] = rxnsup
        .map(rxn => (SubProductPair(rxn.substrates.toList.sorted, List(m)), rxn)).
        groupBy(_._1).
        mapValues(_.map(_._2))

      var oneValid = false
      groupedSubProduct.foreach({ case (subProduct, reactions) =>
        // Let's not show cofactor only reactions for now
        if (!subProduct.substrates.forall(cofactors.contains)) {
          val reactionsNode = rxn_node(reactions.map(r => Long.valueOf(r.rxnid)), subProduct)

          val subProductNetworks = subProduct.substrates.map(s => (s, get_cascade(s, depth + 1, Option(if (depth == 0) m else source.get))))
          if (subProductNetworks.forall(_._2.isDefined)) {
            oneValid = true
            subProductNetworks.foreach(s => {
              network.addNode(reactionsNode, reactions.head.rxnid)

              subProduct.products.foreach(p => network.addEdge(create_edge(reactionsNode, mol_node(p))))

              network.mergeInto(s._2.get)

              // add edges of form "s" -> respective "r" nodes
              network.addEdge(create_edge(mol_node(s._1), reactionsNode))
            })
          }
        }
      })

      if (!oneValid){
        return None
      }
    }


    Option(network)
  }
}

class Cascade(target: Long) {
  val t = target

  val nw = Cascade.get_cascade(t, 0).get

  def network() = nw

  def dot(): String = nw.toDOT

}
