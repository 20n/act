import AssemblyKeys._ 

assemblySettings

name := "reachables"

version := "0.1"

scalaVersion := "2.10.3"

resolvers ++= Seq("PaxTools from BioPax.org" at "http://www.biopax.org/m2repo/releases/",
                  "Akka Repository" at "http://repo.akka.io/releases/"
                )

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq( 
      "org.mongodb" %% "casbah" % "2.7.1"
      , "commons-logging" % "commons-logging" % "1.1.1"
      , "commons-discovery" % "commons-discovery" % "0.2"
      , "commons-configuration" % "commons-configuration" % "1.10"
      , "com.google.gwt" % "gwt-dev" % "2.6.1"
      , "com.google.gwt" % "gwt-user" % "2.6.1"
      , "commons-lang" % "commons-lang" % "2.6"
      , "org.json" % "json" % "20140107"
      , "org.jgrapht" % "jgrapht" % "0.9.0"
      , "org.jgrapht" % "jgrapht-core" % "0.9.0"
      , "org.mod4j.org.apache.commons" % "cli" % "1.0.0"
      , "org.apache.commons" % "commons-lang3" % "3.3.2"
      , "org.apache.axis" % "axis" % "1.4"
      , "org.apache.axis" % "axis-jaxrpc" % "1.4"
      , "com.thoughtworks.xstream" % "xstream" % "1.4.7"
      , "org.mortbay.jetty" % "servlet-api" % "3.0.20100224"
      , "org.mortbay.jetty" % "jetty" % "7.0.0.pre4"
      , "net.sf.opencsv" % "opencsv" % "2.0"
      /* 
       * paxtools for metacyc processing 
       * we get paxtools from the biopax resolver 
       */
      , "org.biopax.paxtools" % "paxtools" % "4.2.0"
      , "org.biopax.paxtools" % "paxtools-core" % "4.2.0",
      /*
       * We have to hack around the fact that spark includes akka v2.2.3
       * The way to do that is to include the akka dependencies 
       * BEFORE the spark include following this section. Then in assembly 
       * mergeStrategy we use MergeStrategy.last for all akka includes that picks
       * only the spark included files, and nothing from this section
       */
      "io.spray"            %%  "spray-can"     % sprayV,
      "io.spray"            %%  "spray-routing" % sprayV,
      "io.spray"            %%  "spray-caching" % sprayV,
      "io.spray"            %%  "spray-testkit" % sprayV  % "test",
      "com.typesafe.akka"   %%  "akka-remote"   % akkaV,
      "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
      "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
      "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test",
      /* 
       * spark for distributed processing
       */
      "org.apache.spark" %% "spark-core" % "1.0.2"
/*
 * the maven repo jar seem to be outdated, or incompatible. 
 * we posted to the indigo group bugs. The current resolution
 * is to use unmanaged jars we have locally. Soft link
 * lib/indigo{-inchi.jar, -renderer.jar, .jar} and lib/jna.jar
 *            , "com.ggasoftware" % "indigo" % "1.1.12"
 *            , "com.ggasoftware.indigo" % "indigo-renderer" % "1.1.12"
 *            , "com.ggasoftware.indigo" % "indigo-inchi" % "1.1.12"
*/
     )
}

Revolver.settings

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("com", "esotericsoftware", "minlog", xs)        => if (xs.startsWith("Log")) MergeStrategy.last else MergeStrategy.deduplicate
    case PathList("javax", "annotation", "meta", "When.class")    => MergeStrategy.first
    case PathList("javax", "xml", "namespace", xs @ _*)           => MergeStrategy.first
    case PathList("javax", "servlet", xs @ _*)                    => MergeStrategy.first
    case PathList("org", "apache", "commons", "lang", xs @ _*)    => MergeStrategy.first
    case PathList("org", "apache", "commons", "logging", xs @ _*) => MergeStrategy.first
    case PathList("org", "apache", "commons", "codec", xs @ _*)   => MergeStrategy.last
    case PathList("org", "apache", "commons", "lang3", xs @ _*)   => MergeStrategy.last
    case PathList("org", "apache", "tools", xs @ _*)              => MergeStrategy.last
    case PathList("org", "eclipse", "jetty", xs @ _*)             => MergeStrategy.last
    case PathList("org", "xmlpull", "v1", xs @ _*)                => MergeStrategy.first
    /* 
     * This is a hack to accomodate the fact that the Spark jar includes AKKA v2.2.6 
     * See comment in libDependencies for the io.spray/com.typesafe.akka that occur
     * BEFORE the spark include. That way when we package the spark assembly and choose
     * "last" as the strategy below, only the spark jars are picked
     */
    case PathList("akka", xs @ _*)                                => MergeStrategy.last
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("mailcap" :: Nil) | ("eclipsef.rsa" :: Nil) | ("eclipsef.sf" :: Nil) | 
              ("index.list" :: Nil) | ("manifest.mf" :: Nil) |
              ("eclipse.inf" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith("pom.properties") | ps.last.endsWith("pom.xml") =>
          MergeStrategy.discard
        case _ => MergeStrategy.deduplicate
      }
    case "plugin.properties" => MergeStrategy.discard
    case x => old(x)
  }
}

