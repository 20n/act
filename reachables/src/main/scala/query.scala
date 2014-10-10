package com.act.query

object solver {
  val instance = new solver
  def solve = instance.solve _
}

class solver {
  def solve(query: String) = {
    println("solving q: " + query)
    query
  }
}
