package com.act.reachables


import com.act.reachables.PathwayConstructor.ComplexPath

import scala.collection.JavaConverters._
import scala.collection.mutable


class PathwayConstructor(sourceNetwork: Network) {
  def getAllPaths(target: Long, level: Int = 0): List[ComplexPath] = {
    if (Cascade.is_universal(target)) {
      List(ComplexPath(sourceNetwork.idToNode.get(target), None, None, level))
    } else if (sourceNetwork.getEdgesGoingInto(target) == null || sourceNetwork.getEdgesGoingInto(target).isEmpty) {
      // Caused by cofactor filtering wherein the chemical is only produced by cofactors, 
      // but we didn't add the cofactors to the network originally.
      List(ComplexPath(sourceNetwork.idToNode.get(target), None, None, level))
    } else {
      val reactionsThatProduceTarget: List[Edge] = sourceNetwork.getEdgesGoingInto(target).asScala.toList

      reactionsThatProduceTarget.map(reactionEdge => {
        val reactionNode = reactionEdge.src

        // Get all the needed nodes for this reaction, then filter out cofactors to get the needed chems
        val requiredChemicalEdges: List[Edge] = sourceNetwork.getEdgesGoingInto(reactionNode).asScala.toList
        val chemicalsNeededForThisReaction: List[Node] = requiredChemicalEdges.map(_.src).filter(s => !Cascade.cofactors.contains(s.id))

        // Need a list of this path + all the other paths
        val producerPaths: List[List[ComplexPath]] = chemicalsNeededForThisReaction.map(c => {
          getAllPaths(c.id, level + 1)
        })

        val reaction = if (producerPaths.exists(_.nonEmpty))
          Option(reactionEdge)
        else None

        val complexPathsThatProduceCurrentTarget = if (producerPaths.exists(_.nonEmpty))
          Option(producerPaths.filter(_.nonEmpty))
        else None

        ComplexPath(reactionEdge.dst, reaction, complexPathsThatProduceCurrentTarget, level)
      })
    }
  }

  def createNetworksFromPath(cPath: List[ComplexPath]): List[Network] = {
    // Get all the values that produce a given needed chemical in this path.
    // This returns all valid subgraphs that constitute a path (All chemical dependencies are met, 
    // starting from a native.
    cPath.filter(p => p.reaction.isDefined).flatMap(createAllNetworks(_, sourceNetwork))
  }

  private def createAllNetworks(path: ComplexPath, sourceNetwork: Network): List[Network] = {
    val MAX_COMBINATIONS_OF_SUBSTRATES = 15
    
    if (path.reaction.isEmpty) return List(new Network("native"))

    // Create all viable combinations of pathways from this complex path's producers
    val possibleSubsequentPaths: List[List[ComplexPath]] = path.producers match {
      case Some(x) => PathwayConstructor.chooseOneFromEach(x)
    }

    val resultingGraphs = possibleSubsequentPaths.map(eachPath => {

      // Each path is a group of chemicals that we need the path for
      // See chooseOneFromEach for an explanation of what it looks like.
      val eachNetworkPath = eachPath.map(createAllNetworks(_, sourceNetwork))
      val neededPaths: List[List[Network]] = PathwayConstructor.chooseOneFromEach[Network](eachNetworkPath).take(MAX_COMBINATIONS_OF_SUBSTRATES)

      // A "needed path" is composed of all the combinations of subsequent reactions
      // and chemicals to satisfy the chemical needs of the current reaction.
      neededPaths.map(x => {
        val newInstance = new Network("pathway")

        // Add the current level to the graph
        newInstance.addNode(path.produced, path.produced.id)
        newInstance.addNode(path.reaction.get.src, path.reaction.get.src.id)
        newInstance.addEdge(path.reaction.get)
        val relatedEdges: List[Edge] = sourceNetwork.getEdgesGoingInto(path.reaction.get.src).asScala.toList

        relatedEdges.foreach(e => newInstance.addNode(e.src, e.src.id))
        relatedEdges.foreach(e => newInstance.addEdge(e))

        // We add in all the information found in this given needed path
        x.foreach(newInstance.mergeInto)
        newInstance
      })
    })

    // The network is self-contained, so it no longer needs the combination structure we used, thus we flatten it.
    resultingGraphs.flatten
  }
}

object PathwayConstructor {
  // A complex path is a simplification of the network that I use to make later subgraph construction easier.
  // It is a recursive data structure that holds a produced chemical, reaction, 
  // and the possible paths to making the substrates of the reaction.
  case class ComplexPath(produced: Node, reaction: Option[Edge], producers: Option[List[List[ComplexPath]]], level: Int = 0) {
    override def toString(): String = {
      s"""
         |${"\t"*level}Produced: ${produced.id}
         |${"\t"*level}Reaction: $reaction
         |${"\t"*level}Producers: $producers
        """.stripMargin
    }
  }

  private def chooseOneFromEach[T](input: List[List[T]]): List[List[T]] = {
    // This function takes in a list of lists and returns a list of lists, wherein the return list has
    // constructed a new list that is all the combinations of choosing one element from the each of the input lists.
    //
    // For example, if I give you the list List(List(A, B), List(C))
    // The expected return is List(List(A, C), List(B, C))

    // We use a closure here so that we can fill this list as we fill in the last element and
    // not have to worry about the complexities of recursing back all this
    val fullList = mutable.ListBuffer[List[T]]()

    def chooseAll(remainingInput: List[List[T]], createdListSoFar: List[T] = List()): Unit = {
      val headElements: List[T] = remainingInput.head

      val tailElements: List[List[T]] = remainingInput.tail

      // If the tail is empty, there are no more items in the list to add
      if (tailElements.isEmpty) {
        // We are done so we add it to our list of combinations
        //
        // This is the base case (We found the end, and therefore have created a single
        // combination that contains one element from each of the input lists.
        headElements.foreach(x => fullList.append(createdListSoFar ::: List(x)))
        return
      }

      // This takes the previous path so far and creates new lists containing the last element.
      // For example:
      //
      // createdListSoFar = List()
      // Input = List(List(A, B), List(C), List(D))
      // Head = List(A,B)
      // Tail = List(List(C), List(D))
      //
      // We then have two recursive calls:
      // 1) chooseAll(List(List(C), List(D)), List() ::: List(A))
      // 2) chooseAll(List(List(C), List(D)), List() ::: List(B))
      //
      // Thus, we take each element in the head list and create a new list where each value is concatenated
      // We then pass the rest of the lists on so that those can be added.
      headElements.foreach(x => chooseAll(tailElements, createdListSoFar ::: List(x)))
    }
    chooseAll(input)

    // Make our list immutable before returning
    fullList.toList
  }
}
