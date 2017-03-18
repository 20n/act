/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.reachables

import com.act.reachables.PathwayConstructor.ComplexPath

import scala.collection.JavaConverters._

class PathwayConstructor(sourceNetwork: Network) {
  def getAllPaths(target: Long, level: Int = 0): List[Option[ComplexPath]] = {
    val targetNode: Node = sourceNetwork.idToNode.get(target)
    val edgesGoingIntoTargetNode: java.util.Set[Edge] = sourceNetwork.getEdgesGoingInto(target)
    
    if (Cascade.is_universal(target)) {
      List(Some(ComplexPath(targetNode, None, None, level)))
    } else if (edgesGoingIntoTargetNode == null || edgesGoingIntoTargetNode.isEmpty) {
      // Caused by cofactor filtering wherein the chemical is only produced by cofactors, 
      // but we didn't add the cofactors to the network originally.
      List(Some(ComplexPath(targetNode, None, None, level)))
    } else if (level > 10) {
      // Probably a cycle and even if not the pathway is likely too long
      List(None)
    } else {
      val reactionsThatProduceTarget: List[Edge] = edgesGoingIntoTargetNode.asScala.toList

      reactionsThatProduceTarget.map(reactionEdge => {
        val reactionNode = reactionEdge.src

        // Get all the needed nodes for this reaction, then filter out cofactors to get the needed chems
        val requiredChemicalEdges: List[Edge] = sourceNetwork.getEdgesGoingInto(reactionNode).asScala.toList
        val chemicalsNeededForThisReaction: List[Node] = requiredChemicalEdges.map(_.src).filter(s => !Cascade.cofactors.contains(s.id))

        // Need a list of this path + all the other paths
        val producerPaths: List[List[Option[ComplexPath]]] = chemicalsNeededForThisReaction.map(c => {
          getAllPaths(c.id, level + 1)
        })

        // Could not produce one of the needed members without a cycle
        if (producerPaths.exists(_.isEmpty)) 
          None 
        else {
          val reaction = if (producerPaths.nonEmpty)
            Option(reactionEdge)
          else 
            None

          val complexPathsThatProduceCurrentTarget = if (producerPaths.nonEmpty)
            Option(producerPaths.map(x => x.flatMap(y => y)))
          else 
            None
          
          Some(ComplexPath(reactionEdge.dst, reaction, complexPathsThatProduceCurrentTarget, level))
        }
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
    val MAX_COMBINATIONS_OF_SUBSTRATES = 5
    
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
    override def toString: String = {
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
    input match {
      case hd :: tl => hd.flatMap(h => chooseOneFromEach(tl).map(t => h :: t))
      case _ => List(List())
    }
  }
}
