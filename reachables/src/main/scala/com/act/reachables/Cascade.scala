package com.act.reachables

import org.apache.commons.codec.digest.DigestUtils

import scala.collection.JavaConversions._

object Cascade extends Falls {

  // depth upto which to generate cascade data
  var max_cascade_depth = GlobalParams.MAX_CASCADE_DEPTH

  // the best precursor reaction
  var cache_bestpre_rxn = Map[Long, Set[ReachRxn]]()

  // the cache of the cascade if it has been
  // previously computed
  var cache_nw = Map[Long, Network]()

  // We only pick rxns that lead monotonically backwards in the tree.
  // This is conservative to avoid cycles (we could be optimistic and jump
  // fwd in the tree, if the rxn is really good, but we risk infinite loops then)

  def pre_rxns(m: Long): Set[ReachRxn] = if (cache_bestpre_rxn contains m) cache_bestpre_rxn(m) else {

    // incoming unreachable rxns ignored
    val upReach = upR(m).filter(_.isreachable)

    // we dont want to use reactions that dont have any substrates (most likely bad data)
    val upNonTrivial = upReach.filter(has_substrates)

    // to avoid circular paths, we require the precuror rxn to go towards natives
    val up = upNonTrivial.filter(higher_in_tree(m, _))

    // add to cache
    cache_bestpre_rxn = cache_bestpre_rxn + (m -> up)

    // onwards, and upwards!
    up
  }

  // dot does not like - in identifiers. Replace those with underscores
  def rxn_node_ident(id: Long) = 4000000000l + id
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
      case Some(ecnum) => "javascript:window.open('http://brenda-enzymes.org/enzyme.php?ecno=" + ecnum + "'); "
    }
  }
  def rxn_node(id: Long) = {
    if (id > GlobalParams.FAKE_RXN_ID) {
      val num_omitted = id - GlobalParams.FAKE_RXN_ID
      val node = Node.get(id, true)
      Node.setAttribute(id, "isrxn", "true")
      Node.setAttribute(id, "label_string", quote(num_omitted + " more"))
      Node.setAttribute(id, "tooltip_string", quote(num_omitted + " more"))
      Node.setAttribute(id, "url_string", quote(""))
      node
    } else {
      val ident = rxn_node_ident(id)
      val node = Node.get(ident, true)
      Node.setAttribute(ident, "isrxn", "true")
      Node.setAttribute(ident, "label_string", quote(rxn_node_label_string(id)))
      Node.setAttribute(ident, "tooltip_string", quote(rxn_node_tooltip_string(id)))
      Node.setAttribute(ident, "url_string", quote(rxn_node_url_string(id)))
      node
    }
  }
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

  def fixed_sz_svg_img(id: Long) = {
    // From: http://www.graphviz.org/content/images-nodes-label-below
    // Put DOT label like so:
    // <<TABLE border="0" cellborder="0"> <TR><TD width="60" height="50" fixedsize="true">
    // <IMG SRC="20n.png" scale="true"/></TD><td><font point-size="10">protein2ppw</font></td></TR></TABLE>>

    // Generate md5 hash for inchi
    val md5 = DigestUtils.md5Hex(ActData.instance().chemId2Inchis.get(id))
    // Format the rendering filename
    val renderingFilename = String.format("molecule-%s.png", md5)

    // Construct the string
    "<<TABLE border=\"0\" cellborder=\"0\"> " +
      "<TR><TD width=\"120\" height=\"100\" fixedsize=\"true\"><IMG SRC=\"" +
      renderingFilename +
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

  def create_edge(src: Node, dst: Node) = Edge.get(src, dst, true);

  def set_max_cascade_depth(depth: Integer) {
    max_cascade_depth = depth
  }

  def get_cascade(m: Long, depth: Int): Network = if (cache_nw contains m) cache_nw(m) else {
    val nw = new Network("cascade_" + m)
    nw.addNode(mol_node(m), m)

    if (depth > max_cascade_depth || is_universal(m)) {
      // do nothing, base case
    } else {
      val rxnsup = pre_rxns(m)

      // limit the # of up reactions to output to MAX_CASCADE_UPFANOUT
      // compute all substrates "s" of all rxnsups (upto 10 of them)
      rxnsup.take(GlobalParams.MAX_CASCADE_UPFANOUT).foreach{ rxn =>
        // add all rxnsup as "r" nodes to the network
        nw.addNode(rxn_node(rxn.rxnid), rxn.rxnid)

        // add edges of form "r" node -> m into the network
        nw.addEdge(create_edge(rxn_node(rxn.rxnid), mol_node(m)))

        rxn.substrates.foreach{ s =>
          // get_cascade on each of "s" and merge that network into nw
          val cascade_s = get_cascade(s, depth + 1)
          nw.mergeInto(cascade_s)

          // add edges of form "s" -> respective "r" nodes
          nw.addEdge(create_edge(mol_node(s), rxn_node(rxn.rxnid)))
        }
      }

      // if the rxns set contains a lot of up rxns (that we dropped)
      // add a message on the network so that its clear not all
      // are being shown in the output
      if (rxnsup.size > GlobalParams.MAX_CASCADE_UPFANOUT) {
        val num_omitted = rxnsup.size - GlobalParams.MAX_CASCADE_UPFANOUT
        val fakerxnid = GlobalParams.FAKE_RXN_ID + num_omitted
        nw.addNode(rxn_node(fakerxnid), fakerxnid)
        nw.addEdge(create_edge(rxn_node(fakerxnid), mol_node(m)))
      }
    }

    // return this accumulated network
    nw
  }

}

class Cascade(target: Long) {
  val t = target

  val nw = Cascade.get_cascade(t, 0)

  def network() = nw

  def dot(): String = nw.toDOT

}
