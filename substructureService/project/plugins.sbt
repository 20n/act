addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

/* This neat plugin can print a complete dependency tree for your project.  Run:
 * $ sbt dependencyTree
 * Try it right now!  You won't regret it!  Unless you have a lot of important history in your terminal, which will be
 * wiped out by the ridiculous number of lines this task produces. */
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")
