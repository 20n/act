package com.act.reachables


import scala.collection.JavaConverters._
import scala.collection.mutable


object PathwayConstructor {
  def getAllPaths(network: Network, target: Long, level: Int = 0): List[ComplexPath] = {
    if (Cascade.is_universal(target)) {
      return List(ComplexPath(network.idToNode.get(target), None, None, level))
    }

    if (network.getEdgesGoingInto(target) == null || network.getEdgesGoingInto(target).isEmpty) {
      return List()
    }
    val reactionsThatProduceTarget: List[Edge] = network.getEdgesGoingInto(target).asScala.toList

    reactionsThatProduceTarget.map(reactionEdge => {
      val reactionNode = reactionEdge.src

      // Get all the needed nodes for this reaction, then filter out cofactors to get the needed chems
      val requiredChemicalEdges: List[Edge] = network.getEdgesGoingInto(reactionNode).asScala.toList
      val chemicalsNeededForThisReaction: List[Node] = requiredChemicalEdges.map(_.src).filter(s => !Cascade.cofactors.contains(s.id))

      // Need a list of this path + all the other paths
      val producerPaths: List[List[ComplexPath]] = chemicalsNeededForThisReaction.map(c => {
        getAllPaths(network, c.id, level+1)
      })
      ComplexPath(reactionEdge.dst, if (producerPaths.exists(_.nonEmpty)) Option(reactionEdge) else None, if (producerPaths.exists(_.nonEmpty)) Option(producerPaths) else None, level)
    })
  }


  case class ComplexPath(produced: Node, reaction: Option[Edge], producers: Option[List[List[ComplexPath]]], level: Int = 0) {
    override def toString(): String = {
        s"""
          |${"\t"*level}Produced: ${produced.id}
          |${"\t"*level}Reaction: $reaction
          |${"\t"*level}Producers: $producers
        """.stripMargin
    }
  }

  def createNetworksFromPath(cPath: List[ComplexPath], sourceNetwork: Network): List[Network] = {
    // Get all the values that produce a given needed chemical in this path.
    cPath.filter(p => p.reaction.isDefined).flatMap(createAllNetworks(_, sourceNetwork))
  }

  def createAllNetworks(path: ComplexPath, sourceNetwork: Network): List[Network] = {
    if (path.reaction.isEmpty) return List(new Network("native"))

    // Create all viable combinations of pathways from this complex path's producers
    val possibleSubsequentPaths: List[List[ComplexPath]] = path.producers match {
      case Some(x) => chooseOneFromEach(x)
    }
    println(
      s"""
         |
         |Before: ${path.producers}
         |After: $possibleSubsequentPaths
         |
       """.stripMargin)

    val resultingGraphs = possibleSubsequentPaths.map(eachPath => {
      // Each path is a group of chemicals that we need the path for
      val neededPaths: List[List[Network]] = chooseOneFromEach[Network](eachPath.map(createAllNetworks(_, sourceNetwork)))
      neededPaths.map(x => {
        val newInstance = new Network("pathway")
        newInstance.addNode(path.produced, path.produced.id)
        newInstance.addNode(path.reaction.get.src, path.reaction.get.src.id)
        newInstance.addEdge(path.reaction.get)

        val relatedEdges: List[Edge] = sourceNetwork.getEdgesGoingInto(path.reaction.get.src).asScala.toList

        relatedEdges.foreach(e => newInstance.addNode(e.src, e.src.id))
        relatedEdges.foreach(e => newInstance.addEdge(e))

        x.foreach(newInstance.mergeInto)
        newInstance
      })
    })

    resultingGraphs.flatten
  }

  private def chooseOneFromEach[T](input: List[List[T]]): List[List[T]] = {
    val fullList = mutable.ListBuffer[List[T]]()

    def chooseAll(remainingInput: List[List[T]], createdListSoFar: List[T] = List()): Unit = {
      val headElements: List[T] = remainingInput.head

      val tailElements: List[List[T]] = remainingInput.tail
      if (tailElements.isEmpty) {
        // Woo we are done so we add it to our list of combinations
        headElements.foreach(x => fullList.append(createdListSoFar ::: List(x)))
        return
      }

      headElements.foreach(x => chooseAll(tailElements, createdListSoFar ::: List(x)))
    }
    chooseAll(input)

    fullList.toList
  }
}
