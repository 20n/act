package com.act.reachables

import java.io.File
import java.lang.Long
import java.util
import java.util.NoSuchElementException

import act.shared.{Seq => DbSeq}
import com.act.analysis.proteome.scripts.OddSequencesToProteinPredictionFlow
import com.act.reachables.Cascade.NodeInformation
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoKeywords, SequenceKeywords}
import com.fasterxml.jackson.annotation._
import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.{BasicDBList, BasicDBObject, DB, MongoClient, ServerAddress}
import org.apache.commons.codec.digest.DigestUtils
import org.apache.logging.log4j.LogManager
import org.mongojack.JacksonDBCollection

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable

// Default host. If running on a laptop, please set a SSH bridge to access speakeasy

object Cascade extends Falls {
  val mongoClient: MongoClient = new MongoClient(new ServerAddress("localhost", 27017))
  val db: DB = mongoClient.getDB("wiki_reachables")
  var collectionName: String = "pathways_jarvis_dec21"
  def setCollectionName(c: String) {
    collectionName = c
  }

  var CACHE_CASCADES = true
  def doCacheCascades(onOff: Boolean) {
    println("Cascades caching is: " + (if (onOff) "ON" else "OFF"))
    CACHE_CASCADES = onOff
  }

  var DO_HMMER_SEQ = true
  def doHmmerSeqFinding(enable: Boolean) {
    println("HMMER sequence finding is: " + (if (enable) "ON" else "OFF"))
    DO_HMMER_SEQ = enable
  }

  var RUN_LONGER_BUT_USE_LESS_MEM = false
  def doFrequentCachePurges(enable: Boolean) {
    println("Frequent cache purges to save memory (but run longer): " + (if (enable) "ON" else "OFF"))
    RUN_LONGER_BUT_USE_LESS_MEM = enable
  }

  var VERBOSITY = 2
  def setVerbosity(v: Integer) {
    println("Verbosity set to: " + v)
    VERBOSITY = v
  }

  lazy val pathwayCollection: JacksonDBCollection[ReactionPath, String] = JacksonDBCollection.wrap(db.getCollection(collectionName), classOf[ReactionPath], classOf[String])

  def get_pathway_collection: JacksonDBCollection[ReactionPath, String] = {
    pathwayCollection
  }

  case class SubProductPair(substrates: List[Long], products: List[Long])

  var nodeMerger: mutable.HashMap[SubProductPair, Node] = new mutable.HashMap()

  // depth upto which to generate cascade data
  var max_cascade_depth = GlobalParams.MAX_CASCADE_DEPTH

  val cacheBnd = Cascade.RUN_LONGER_BUT_USE_LESS_MEM match {
    case true => Some(1000)
    case false => None
  }
  // the best precursor reaction
  val cache_bestpre_rxn = getCaffeineCache[Long, Map[SubProductPair, List[ReachRxn]]](cacheBnd)

  // the cache of the cascade if it has been previously computed
  val cache_nw = getCaffeineCache[Long, Option[Network]](cacheBnd)

  def getCaffeineCache[T, S](optBound: Option[Int]) = {
    val caffeine = Caffeine.newBuilder().asInstanceOf[Caffeine[T, S]]
    if (optBound.isDefined)
      caffeine.maximumSize(optBound.get)
    val cache = caffeine.build[T, S]()
    cache
  }

  // We only pick rxns that lead monotonically backwards in the tree.
  // This is conservative to avoid cycles (we could be optimistic and jump
  // fwd in the tree, if the rxn is really good, but we risk infinite loops then)

  def pre_rxns(m: Long, higherInTree: Boolean = true): Map[SubProductPair, List[ReachRxn]] = {
    if (higherInTree && cache_bestpre_rxn.asMap.containsKey(m)) {
      return cache_bestpre_rxn.asMap.get(m)
    }

    // incoming unreachable rxns ignored
    val upReach = upR(m).filter(_.isreachable)

    // we dont want to use reactions that dont have any substrates (most likely bad data)
    val upNonTrivial = upReach.filter(has_substrates)

    val groupedSubProduct: Map[SubProductPair, List[ReachRxn]] = upNonTrivial.toList
      .map(rxn => (SubProductPair(rxn.substrates.toList.filter(x => !cofactors.contains(x)).sorted, List(m)), rxn)).
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

      if (higherInTree) {
        cache_bestpre_rxn.put(m, passingGrouped)
      }

      passingGrouped
    }
  }

  // Shift rxn ids outside of the range of molecule ids so that there is no collision between molecule and reaction ids
  val rxnIdShift = 4000000000l

  val toolTipReplacePattern = """:\[([\s\d\,]*)\]""".r

  // dot does not like - in identifiers. Replace those with underscores
  def rxn_node_ident(id: Long) = rxnIdShift + id
  def rxn_node_rxn_ident(id: Long) = id - rxnIdShift
  def mol_node_ident(id: Long) = id

  def rxn_node_tooltip_string(id: Long) = {
    ReachRxnDescs.rxnEasyDesc(id) match {
      case None => "ID:" + id + " not in DB"
      // GraphViz chokes on "[" and "]". Replace these with "{" and "}"
      case Some(desc) => toolTipReplacePattern.replaceAllIn(desc, m => s":{${m.group(1)}}")

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
    val labelSet = new util.HashSet[String](ids.map(id => rxn_node_label_string(id)).toSet)

    val convertedIds = ids.map(x => rxn_node_ident(x): java.lang.Long)

    // Get sorted list of organisms
    val organisms = ids.flatMap(id => ReachRxnDescs.rxnOrganismNames(id).get).sorted(Ordering[String].reverse)

    if (nodeMerger.contains(unique)){
      val previouslyCreatedNode = nodeMerger(unique)
      val ident = previouslyCreatedNode.id

      Node.getAttribute(ident, "reaction_ids").asInstanceOf[util.HashSet[Long]].add(ident)
      val s: util.HashSet[Long] = (Node.getAttribute(ident, "reaction_ids").asInstanceOf[util.HashSet[Long]])
      s.addAll(convertedIds)

      Node.setAttribute(ident, "reaction_ids", s)
      Node.setAttribute(ident, "reaction_count", s.size())

      val current =  Node.getAttribute(ident, "label_string").asInstanceOf[util.HashSet[String]]
      current.addAll(labelSet)
      Node.setAttribute(ident, "label_string", current)

      val addedOrganisms: Set[String] = Node.getAttribute(ident, "organisms").asInstanceOf[util.HashSet[String]].toSet ++ organisms
      Node.setAttribute(ident, "organisms",  new util.HashSet(addedOrganisms.asJava))
      return nodeMerger(unique)
    }

    val ident = rxn_node_ident(ids.head)
    val node = Node.get(ident, true)
    Node.setAttribute(ident, "isrxn", "true")
    val ridSet = new util.HashSet[Long]()
    ridSet.addAll(convertedIds.asJava)
    Node.setAttribute(ident, "reaction_ids", ridSet)
    Node.setAttribute(ident, "reaction_count", ridSet.size())
    Node.setAttribute(ident, "label_string", labelSet)
    Node.setAttribute(ident, "tooltip_string", rxn_node_tooltip_string(ids.head))
    Node.setAttribute(ident, "url_string", rxn_node_url_string(ids.head))
    Node.setAttribute(ident, "organisms", new util.HashSet(organisms.asJava))
    nodeMerger.put(unique, node)
    node
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

  def fixed_sz_svg_img(id: Long): String = {
    // From: http://www.graphviz.org/content/images-nodes-label-below
    // Put DOT label like so:
    // <<TABLE border="0" cellborder="0"> <TR><TD width="60" height="50" fixedsize="true">
    // <IMG SRC="20n.png" scale="true"/></TD><td><font point-size="10">protein2ppw</font></td></TR></TABLE>>

    val inchi = ActData.instance().chemId2Inchis.get(id)
    // Generate md5 hash for inchi
    val md5 = DigestUtils.md5Hex(if (inchi == null) "" else inchi)
    // Format the rendering filename
    val renderingFilename = new File("/mnt/data-level1/data/reachables-explorer-rendering-cache/", String.format("molecule-%s.png", md5)).getAbsolutePath

    val readableName = ActData.instance.chemId2ReadableName.get(id)

    val myName = if (readableName == null){
      "null"
    } else if (readableName.startsWith("InChI")){
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

  def addValid(m: Long, depth: Int, source: Option[Long], seen: Set[Long], network: Network, candidate: (SubProductPair, List[ReachRxn])) = {
    val oneValid = candidate match {
      case (subProduct, reactions) => {
        val substrates = subProduct.substrates
        val products = subProduct.products

        if (substrates.exists(seen.contains(_))) {
          // this is not a valid candidate as it leads back to descendent (in `seen`) and will create cycle
          false
        } else {
          // True for only cofactors and empty ist.
          if (substrates.forall(cofactors.contains)) {
            // Let this node be activated as it is activated by a coefficient only rxn
            true
          } else {
            val reactionsNode = rxn_node(reactions.map(r => Long.valueOf(r.rxnid)), subProduct)

            def recurse(s: Long) = {
              val src = if (depth == 0) Option(m) else source
              (s, get_cascade(s, depth+1, src, seen + m))
            }
            val subProductNetworks = substrates.map(recurse)
            if (subProductNetworks.forall(_._2.isDefined)) {
              subProductNetworks.foreach(s => {
                network.addNode(reactionsNode, rxn_node_ident(reactions.head.rxnid))
                products.foreach(p => network.addEdge(create_edge(reactionsNode, mol_node(p))))
                network.mergeInto(s._2.get)
                // add edges of form "s" -> respective "r" nodeMapping
                network.addEdge(create_edge(mol_node(s._1), reactionsNode))
              })

              // this is a valid up-edge in the cascade
              true
            } else {
              // at last one substrate cannot be traversed all the way back to cofactors
              // so this edge cannot be a valid edge going upwards in cascade
              false
            }
          }
        }
      }
    }

    oneValid
  }

  def get_cascade(m: Long, depth: Int = 0, source: Option[Long] = None, seen: Set[Long] = Set()): Option[Network] =
  if (CACHE_CASCADES && depth > 0 && cache_nw.asMap.containsKey(m)) cache_nw.asMap.get(m) else
  {
    // first check if we are "re-getting" the cascade for the main target,
    // and if so return empty. this allows us to break cycles around the target
    if (source.isDefined && source.get == m) return None

    val network = new Network("cascade_" + m)
    network.addNode(mol_node(m), m)

    val optUpwardsCascade = if (is_universal(m)) {
      // do nothing, base case
      Some(network)
    } else {
      // We don't filter by higher in tree on the first iteration, so that all possible
      // reactions producing this product are shown on the graph.
      val grouped: List[(SubProductPair, List[ReachRxn])] = pre_rxns(m, higherInTree = depth != 0).toList

      val validNodes: List[Boolean] = grouped.map(x => addValid(m, depth, source, seen, network, x))
      // find if there was a single node that was valid (take OR of all valid's)
      val oneValid = validNodes.exists(_ == true)

      if (!oneValid && depth > 0){
        None
      } else {
        Some(network)
      }
    }

    // Now we cache the network. Except for two cases:
    // 1) when the node is the target node (i.e., depth == 0), the `optUpwardsCascade` for this node
    //    contains the exceptional case of including edges even if they don't go higher in the tree
    //    so this computation of its `optUpwardsCascade` is a one off. If we encounter this other times (i.e., as
    //    an internal node during some other computation) the exception would not have been applied
    //    and so we'll be good to cache it then.
    // 2) when the node happens to have been explored as part of a cycle (that does not lead to
    //    natives). In that case, we usually do want to exclude it. But there are times when
    //    bidirectional edges exist in the cycle, and so there is a way to use the edges in it
    //    to actually break out of it. To allow for that case, we don't cache when the computation
    //    evaluates to None. Every other case, good to go.
    if (depth > 0 && optUpwardsCascade.isDefined) {
      // cache the network so we don't recompute it
      cache_nw.put(m, optUpwardsCascade)
    }

    optUpwardsCascade
  }

  def getAllPaths(network: Network, target: Long): Option[List[Path]] = {
    val sourceEdgesSet: util.Set[Edge] = network.getEdgesGoingInto(target)

    // If the target is a native then the only path is the node itself.
    var counter: Int = -1
    if (sourceEdgesSet == null) {
      if (network.nodes.isEmpty) {
        return None
      }
      return Option(List(new Path(List(network.nodes.toList.head))))
    }

    val sourceEdges = sourceEdgesSet.asScala.toList

    val paths = Option(sourceEdges.flatMap(e => {
      val path = getPath(network, e)
      if (path.isDefined) {
        counter = counter + 1
        Option(path.get.map(p => new Path(List(e.dst) ::: p.getPath)))
      } else {
        None
      }
    }).flatten)

    if (paths.isEmpty || paths.get.isEmpty) {
      // Use other methods
      val branchedPaths = PathwayConstructor.getAllPaths(network, target)
      val result = PathwayConstructor.createNetworksFromPath(branchedPaths, network)
      println(s"Constructed Pathways: ${result.length}")
      if (result.length == 0) {
        println(s"\n\n\n$branchedPaths\n\n\n")
        System.exit(1)
      }
      Option(result.map(convertNetworkToPath(_, target)))
    } else {
      paths
    }
  }

  def convertNetworkToPath(nw: Network, target: Long): Cascade.Path = {
    val pathList = mutable.ListBuffer[Node]()
    pathList.append(nw.idToNode(target))

    var current: List[Edge] = nw.edgesGoingToId(target).toList
    var frontier = mutable.ListBuffer[Edge]()
    while(current.nonEmpty) {
      current.foreach(e => {
        // Compound => Reaction Edges
        // Add to current
        pathList.append(e.dst)
        pathList.append(e.src)

        // Add all the edges going into this one to the list
        try {
          val resultingNodes = nw.edgesGoingToId(e.src.id)
          resultingNodes.foreach(e => frontier.append(e))
        } catch {
          case e: NoSuchElementException =>
        }
      })

      // Switch frontier with current to get the next level.
      current = frontier.toList
      frontier = mutable.ListBuffer[Edge]()
    }

    new Path(pathList.distinct.toList)
  }


  private def getPath(network: Network, edge: Edge, seenNodes: Set[Node] = Set()): Option[List[Path]] = {
    // Base case
    val reactionNode = edge.src

    // If reaction node has more than one edge we say that this isn't a viable path
    if (network.getEdgesGoingInto(reactionNode).size() > 1) return None

    val substrateNode = network.getEdgesGoingInto(reactionNode).head.src
    if (seenNodes.contains(substrateNode)) {
      return None
    }

    if (cofactors.contains(substrateNode.id)) {
      return Option(List())
    }

    if (is_universal(substrateNode.id)) return Option(List(new Path(List(reactionNode, substrateNode))))

    if (network.getEdgesGoingInto(substrateNode) == null) {
      return Option(List())
    }
    val edgesGoingInto: List[Edge] = network.getEdgesGoingInto(substrateNode).toList

    // Get back a bunch of maybe paths
    val resultingPaths: List[Path] = edgesGoingInto.flatMap(x => {
      val grabPath = getPath(network, x, seenNodes + substrateNode)
      if (grabPath.isDefined) {
        Option(grabPath.get.map(p => new Path(List(reactionNode, substrateNode) ::: p.getPath)))
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

  class Path(path: List[Node]) {
    def getPath: List[Node] ={
      path
    }

    def getDegree(): Int = {
      // This references the first reaction in the path (First element is the product/reachable).
      // Therefore, by counting the reactions associated with this node we get the in-degree of the reachable
      getReactionCount(path.get(1))
    }

    def getReactionSum(): Int ={
      path.map(getReactionCount).sum
    }

    @JsonIgnore
    private def getReactionCount(node: Node): Int = {
      // Only reactions contribute
      // if (Node.getAttribute(node.id, "isrxn") == null)
      if (Node.getAttribute(node.id, "isrxn").asInstanceOf[String].toBoolean) {
        node.getAttribute("reaction_count").asInstanceOf[Int]
      } else {
        0
      }
    }
  }

  // TODO make this discrete blocks so that we seperate concerns better
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonCreator
  class NodeInformation(@JsonProperty("isReaction") var isReaction: Boolean,
                        @JsonProperty("isSpontaneous") var isSpontaneous: Boolean,
                        @JsonProperty("sequences") var sequences: util.HashSet[Long],
                        @JsonProperty("organisms") var organisms: util.HashSet[String],
                        @JsonProperty("reactionIds") var reactionIds: util.HashSet[Long],
                        @JsonProperty("reactionCount") var reactionCount: Int,
                        @JsonProperty("id") var id: Long,
                        @JsonProperty("label") var label: String,
                        @JsonProperty("mostNative") var isMostNative: Boolean = false) {

    def NodeInformation() {}

    def getSequences(): util.HashSet[Long] = {
      sequences
    }

    def setSequences(sequences: util.HashSet[Long]) = {
      this.sequences = sequences
    }

    def getisSpontaneous(): Boolean = {
      isSpontaneous
    }

    def setIsSpontaneous(isSpontaneous: Boolean) = {
      this.isSpontaneous = isSpontaneous
    }

    def getIsReaction(): Boolean ={
      isReaction
    }

    def setIsReaction(isReaction: Boolean) = {
      this.isReaction = isReaction
    }

    def getOrganisms(): util.HashSet[String] = {
      organisms
    }

    def setOrganism(organism: util.HashSet[String]) = {
      this.organisms = organisms
    }

    def getReactionIds(): util.HashSet[Long] = {
      reactionIds
    }

    def setReactionIds(ids: util.HashSet[Long]) = {
      this.reactionIds = ids
    }

    def getReactionCount(): Int = {
      reactionCount
    }

    def setReactionCount(reactionCount: Int) = {
      this.reactionCount = reactionCount
    }

    def getLabel(): String = {
      label
    }

    def setLabel(label: String) = {
      this.label = label
    }

    def getId(): Long = {
      id
    }

    def setId(id: Long) = {
      this.id = id
    }

    def setMostNative(mostNative: Boolean) = {
      this.isMostNative = mostNative
    }

    def getMostNative(): Boolean = {
      this.isMostNative
    }
  }


  def time[T](msg: String)(blk: => T): T = {
    val start = System.nanoTime()
    val rslt = blk
    val end = System.nanoTime()
    val tm = (end - start)/1000000
    if (Cascade.VERBOSITY > 0)
      println(f"## PROFILING: $tm%6dms $msg")
    rslt
  }

}

class Cascade(target: Long) {
  private val logger = LogManager.getLogger(getClass.getName)

  val t = target
  val nw = Cascade.get_cascade(t).get

  private val workingDir = new java.io.File(".").getCanonicalFile
  nw.nodeMapping.values().filter(getOrDefault[String](_, "isrxn").toBoolean).foreach(node => {
    val reactionIds: Set[Long] = getOrDefault[util.HashSet[Long]](node, "reaction_ids", new util.HashSet[Long]()).map(x => Cascade.rxn_node_rxn_ident(x.toLong): Long).toSet
    val isSpontaneous: Boolean = reactionIds.exists(r => {
      val thisSpontaneousResult = ReachRxnDescs.rxnIsSpontaneous(r)
      thisSpontaneousResult.isDefined && thisSpontaneousResult.get
    })
    Node.setAttribute(node.id, "isSpontaneous", isSpontaneous)

    var matchingSequences: Set[Long] = reactionIds.flatMap(r => {
      val thisSequenceResult = ReachRxnDescs.rxnSequence(r)
      thisSequenceResult
    }).flatten.map(_.asInstanceOf[Long])

    // Here we choose to add inferred sequences for all entries matching
    val sequenceSearch: (DbSeq) => Boolean =
      OddSequencesToProteinPredictionFlow.defineSequenceSearch(workingDir)(None)(cascades.DEFAULT_DB._3)

    val abstractOrQuestionableSequencesQuery = OddSequencesToProteinPredictionFlow.oddQuery()
    val theseReactions = new BasicDBList
    matchingSequences.foreach(theseReactions.add)

    // Only do the odd query for sequences matching this reaction.
    val withinTheseReactions = new BasicDBObject(MongoKeywords.IN.toString, theseReactions)
    abstractOrQuestionableSequencesQuery.put(SequenceKeywords.ID.toString, withinTheseReactions)

    val mongoConnection = OddSequencesToProteinPredictionFlow.connectToMongoDatabase(cascades.DEFAULT_DB._3)

    val oddSeqs: List[DbSeq] = mongoConnection.getSeqIterator(abstractOrQuestionableSequencesQuery).asScala.toList

    // TODO Maybe we should only try to infer if there are no/few good sequences.
    // Evaluate how much this helps.  It makes sense as we don't really want to add more to places
    // where there are a lot, but to add some where there are none or few.
    if (Cascade.DO_HMMER_SEQ && matchingSequences.diff(oddSeqs.map(_.getUUID.toLong: Long).toSet).size < 5) {
      // Filter with side effects, eep.
      val anyInferredSeqs: List[DbSeq] = oddSeqs.filter(sequenceSearch)

      /*
      At this point we have three sets,

      A) The overall list of sequences
      B) The list of sequences that were "odd"
      C) The list of sequences that were odd, but we were able to infer a sequence for.

      We want (A - B) + (C) to get all the "good" sequences
    */
      val A: Set[Long] = matchingSequences
      val B: Set[Long] = oddSeqs.map(_.getUUID.toLong: Long).toSet
      val C: Set[Long] = anyInferredSeqs.map(_.getUUID.toLong: Long).toSet

      matchingSequences = A.diff(B).union(C)
      Node.setAttribute(node.id, "hasSequence", matchingSequences.nonEmpty)
      Node.setAttribute(node.id, "sequences", new util.HashSet(matchingSequences))
    } else {
      Node.setAttribute(node.id, "hasSequence", matchingSequences.nonEmpty)
      Node.setAttribute(node.id, "sequences", new util.HashSet(matchingSequences))
    }
  })

  val viablePaths: Option[List[Cascade.Path]] = Cascade.getAllPaths(nw, t)

  val allPaths: List[Cascade.Path] = if (viablePaths.isDefined) {
    viablePaths.get.sortBy(p => (-p.getDegree(), -p.getReactionSum()))
  } else {
    List()
  }

  var c = -1

  // Do any formatting necessary that will be used later on.
  // Things such as coloring interesting paths, setting up strings,
  // and converting reactionIds to the db form are done here.
  val constructedAllPaths: List[ReactionPath] = allPaths.map(p => {
    val rp = new ReactionPath(s"${target}w$c", p.getPath.map(node => {
      val isRxn = getOrDefault[String](node, "isrxn").toBoolean
      val isSpontaneous = getOrDefault[Boolean](node, "isSpontaneous", false)
      val sequences = getOrDefault[util.HashSet[Long]](node, "sequences", new util.HashSet[Long]())
      new NodeInformation(
        isRxn,
        isSpontaneous,
        sequences,
        getOrDefault[util.HashSet[String]](node, "organisms", new util.HashSet[String]()),
        new util.HashSet[Long](getOrDefault[util.HashSet[Long]](node, "reaction_ids", new util.HashSet[Long]()).map(x => Cascade.rxn_node_rxn_ident(x.toLong): Long)),
        getOrDefault[Int](node, "reaction_count", 0),
        node.getIdentifier,
        if (isRxn) {
          getOrDefault[util.HashSet[String]](node, "label_string", new util.HashSet[String]()).mkString(",")
        } else {
          getOrDefault[String](node, "label_string")
        }
      )
    }).asJava)

    val organismStuff = getMostFrequentOrganism(rp)

    rp.setMostCommonOrganism(new util.ArrayList(organismStuff.map(_._1).asJava))
    rp.setMostCommonOrganismCount(new util.ArrayList(organismStuff.map(c => c._2: java.lang.Double)))

    rp
  })


  val sortedPaths = constructedAllPaths.sortBy(p => {
    try {
      -p.getMostCommonOrganismCount.max
    } catch {
      case e: Exception => 0
    }
  })

  if (sortedPaths.nonEmpty) {
    sortedPaths.head.setMostNative(true)

    val limeGreen = "#009933"

    val mostNativePath = sortedPaths.head.getPath.toList.reverse
    for (i <- mostNativePath.indices) {
      if (i >= mostNativePath.length - 1) {
        // Skip last node, has no edge
      } else {
        val sourceNode: Node = network().getNodeById(mostNativePath(i).getId())
        val destNode: Node = network().getNodeById(mostNativePath(i + 1).getId())

        try {
          val edgesGoingInto = network().edgesGoingToNode(destNode)
          val currentEdge: Edge = edgesGoingInto.find(e => e.src.equals(sourceNode)).get

          if (getOrDefault[String](sourceNode, "isrxn").toBoolean) {
            val orgs: util.HashSet[String] = getOrDefault[util.HashSet[String]](sourceNode, "organisms", new util.HashSet[String]())

            if (sortedPaths.head.getMostCommonOrganism.nonEmpty && orgs.contains(sortedPaths.head.getMostCommonOrganism.head)) {
              Edge.setAttribute(currentEdge, "color", f""""$limeGreen", penwidth=5""")
            }
          }

          if (getOrDefault[String](destNode, "isrxn").toBoolean) {
            val orgs: util.HashSet[String] = getOrDefault[util.HashSet[String]](destNode, "organisms", new util.HashSet[String]())
            if (sortedPaths.head.getMostCommonOrganism.nonEmpty && orgs.contains(sortedPaths.head.getMostCommonOrganism.head)) {
              Edge.setAttribute(currentEdge, "color", f""""$limeGreen", penwidth=5""")
            }
          }
        } catch {
          case e: NoSuchElementException =>
        }
      }
    }

    if (Cascade.VERBOSITY > 1) {
      logger.debug(
        s"""
           | Reachable: $target
           | Full Sequence Paths: ${sortedPaths.count(_.getPath.forall(p => !p.isReaction || (p.isReaction && (p.sequences.nonEmpty || p.isSpontaneous))))}
           | Total Paths: ${sortedPaths.length}
        """.stripMargin
      )
    }

    try {
      sortedPaths.foreach(Cascade.pathwayCollection.insert)
    } catch {
      case e: Exception => None
    }
  }

  def getMostFrequentOrganism(p: ReactionPath): List[(String, Double)] = {
    val v: Set[Set[String]] = p.getPath.map(_.getOrganisms().toSet).toSet

    val allKeys: mutable.HashMap[String, Double] = mutable.HashMap()

    // Fill w/ 0s
    for (k <- v.flatten){
      allKeys.put(k, 0)
    }

    // This is the scoring function for organisms, such that we can bias the results towards enzymes that are
    // likely to be more unique to a given organism as they are closer to the reachable.
    //
    // Each step can add a score of 1/(2^n), where n represents the step it is.
    // The exponential decay is what causes the bias towards enzymes close to the reachable.
    //
    // For example, let's say we have a 3 component path such that the reaction to the reachable and the
    // last reaction are a part of the same organism
    //
    // The math is: 1*(1/2^0) + 0*(1/2^1) + 1*(1/2^2) for a total score of 1 + 0 + 1/4 = 1.25
    //
    var c: Double = 1.0
    v.foreach(e => {
      e.foreach(k => allKeys.put(k, allKeys(k) + 1/c))
      c *= 2
    })

    if (allKeys.keySet.toList.isEmpty){
      return List()
    }
    val maxEntry =  allKeys.entrySet().toList.sortBy(p => -p.getValue)

    maxEntry.map(x => (x.getKey, x.getValue))
  }

  def getOrDefault[A](node: Node, key: String, default: A = null): A = {
    val any = node.getAttribute(key)
    if (any == null) {
      default
    } else {
      any.asInstanceOf[A]
    }
  }

  val allStringPaths: List[String] = allPaths.map(currentPath => {val allChemicalStrings: List[String] = currentPath.getPath.flatMap(node => {
    Option(ActData.instance.chemId2ReadableName.get(node.id))
  })
    allChemicalStrings.mkString(", ")
  })


  def network() = nw

  def dot(): String = nw.toDOT

  def getPaths: List[String] = allStringPaths

}
