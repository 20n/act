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

package act.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.shared.helpers.P;

/**
 * Used to store tree structure representing operator paths.
 */
public class RONode {
  private int ro;
  private Long id;
  //private int count; //number of this RO chain (from root to this node) observed
  private int depth;

  /**
   * example paths, organisms.
   */
  private Map<List<Long>,List<Long>> exampleRxn;

  /**
   * Maps id of operator to an RONode.
   * In the database, this mapping will not be stored.
   * Instead it will store a list of ids of RONodes as children.
   */
  private Map<Integer,RONode> children;

  private RONode parent;

  /**
   * Used for db and getting parent node when reading from db.
   */
  private Long parentID;

  public RONode(int ro, Long id) {
    //count = 0;
    this.id = id;
    this.ro = ro;
    children = new HashMap<Integer,RONode>();
    exampleRxn = new HashMap<List<Long>,List<Long>>();
  }

  public RONode getChild(Integer next) {
    return children.get(next);
  }

  public void addChild(RONode node) {
    node.parent = this;
    node.parentID = this.id;
    children.put(node.ro, node);
  }

  /*public void increment() {
    count++;
  }*/

  //public void setCount(int c) { count = c; }
  public void setParentID(Long id) { parentID = id; }
  public void setDepth(int d) { depth = d; }
  public void addExampleRxn(List<Long> rxn, Long org) {
    if(!exampleRxn.containsKey(rxn))
      exampleRxn.put(rxn,new ArrayList<Long>());
    exampleRxn.get(rxn).add(org);

  }

  public Long getID() { return id; }
  public Integer getRO() { return ro; }
  public int getCount() { return exampleRxn.size(); }
  public Set<Integer> getChildren() { return children.keySet(); }
  public RONode getParent() { return parent; }
  public Long getParentID() { return parentID; }
  public int getDepth() { return depth; }
  public Map<List<Long>,List<Long>> getExampleRxn() { return exampleRxn; }

  /**
   * Helper function if you want to just scan through all nodes in tree.
   * @param ro
   */
  public static List<RONode> flattenTree(RONode ro) {
    List<RONode> list = new ArrayList<RONode>();
    flattenTreeHelper(ro, list);
    return list;
  }

  private static void flattenTreeHelper(RONode ro, List<RONode> list) {
    list.add(ro);
    for(Integer next : ro.getChildren()) {
      flattenTreeHelper(ro.getChild(next),list);
    }
  }
}
