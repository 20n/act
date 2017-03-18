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


package com.act.reachables;

import java.io.Serializable;
import java.util.HashMap;

public class Edge implements Serializable {
  private static final long serialVersionUID = 6380350196029629376L;

  Node src, dst;
  protected Edge(Node s, Node d) {
    this.src = s;
    this.dst = d;
  }

  public static Edge get(Node src, Node dst, Boolean create) {
    Edge e = new Edge(src, dst);

    Edge got = ActData.instance().edgeCache.get(e);
    if (got != null) {
      return got;
    }

    if (!create)
      return null;

    // the edge cache does not contain edge. create one
    ActData.instance().edgeCache.put(e, e);

    return e;
  }

  public Node getSrc() { return this.src; }
  public Node getDst() { return this.dst; }

  public HashMap<String, Serializable> getAttr() {
    return ActData.instance().edgeAttributes.get(this);
  }

  public Object getAttribute(String key) {
    return Edge.getAttribute(this, key);
  }

  public static void clearAttributeOnAllEdges(String key) {
    for (HashMap<String, Serializable> attr: ActData.instance().edgeAttributes.values()) {
      attr.remove(key);
    }
  }

  public static void setAttribute(Edge e, String key, Serializable val) {
    if (!ActData.instance().edgeAttributes.containsKey(e))
      ActData.instance().edgeAttributes.put(e, new HashMap<>());
    ActData.instance().edgeAttributes.get(e).put(key, val);
  }

  public static Object getAttribute(Edge e, String key) {
    HashMap<String, Serializable> kval;
    if (ActData.instance().edgeAttributes.containsKey(e) &&
        (kval = ActData.instance().edgeAttributes.get(e)).containsKey(key))
      return kval.get(key);
    else
      return null;
  }

  @Override
  public String toString() {
    return new StringBuilder().append(this.src).append("->").append(this.dst).toString();
  }

  @Override
  public int hashCode() {
    return src.hashCode() ^ dst.hashCode();
  }

  @Override
  public boolean equals(Object e) {
    if (!(e instanceof Edge)) return false;
    Edge other = (Edge) e;
    return this.src.equals(other.src) && this.dst.equals(other.dst);
  }
}
