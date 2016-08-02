import AssemblyKeys._

assemblySettings

name := "reachables"

version := "0.1"

scalaVersion := "2.10.3"

parallelExecution in Test := false

resolvers ++= {
  Seq(
      "PaxTools from BioPax.org" at "http://www.biopax.org/m2repo/releases/",
      "Akka Repository" at "http://repo.akka.io/releases/",
      "BioJava from http://biojava.org/wiki/BioJava:Download" at "http://biojava.org/download/maven/"
     )
}

/* To disable tests during assembly, add this directive: `test in assembly := {}` */
test in assembly := {}

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
      "org.mongodb"             %% "casbah" % "2.7.1"
      , "commons-logging"       % "commons-logging" % "1.1.1"
      , "commons-discovery"     % "commons-discovery" % "0.2"
      , "commons-configuration" % "commons-configuration" % "1.10"
      , "commons-lang"          % "commons-lang" % "2.6"
      , "org.json"              % "json" % "20140107"
      , "org.jgrapht"           % "jgrapht" % "0.9.0"
      , "org.jgrapht"           % "jgrapht-core" % "0.9.0"
      , "org.apache.commons"    % "commons-lang3" % "3.3.2"
      , "org.apache.axis"       % "axis" % "1.4"
      , "org.apache.axis"       % "axis-jaxrpc" % "1.4"
      , "com.thoughtworks.xstream" % "xstream" % "1.4.7"
      , "org.mortbay.jetty"     % "servlet-api" % "3.0.20100224"
      , "org.mortbay.jetty"     % "jetty" % "7.0.0.pre4"
      , "net.sf.opencsv"        % "opencsv" % "2.0"
      , "mysql"                 % "mysql-connector-java" % "5.1.12"
      , "stax"                  % "stax-api" % "1.0.1"
      , "org.rocksdb"           % "rocksdbjni" % "4.1.0"
      /*
       * paxtools for metacyc processing
       * we get paxtools from the biopax resolver
       */
      , "org.biopax.paxtools"   % "paxtools" % "4.2.0"
      , "org.biopax.paxtools"   % "paxtools-core" % "4.2.0",
      /*
       * ChEBI is in OWL format, we use OWL API for parsing
       */
      "net.sourceforge.owlapi"  % "owlapi-distribution" % "3.5.1",
      /*
       * We have to hack around the fact that spark includes akka v2.2.3
       * The way to do that is to include the akka dependencies
       * BEFORE the spark include following this section. Then in assembly
       * mergeStrategy we use MergeStrategy.last for all akka includes that picks
       * only the spark included files, and nothing from this section
      "io.spray"                %%  "spray-can"     % sprayV,
      "io.spray"                %%  "spray-routing" % sprayV,
      "io.spray"                %%  "spray-caching" % sprayV,
      "io.spray"                %%  "spray-testkit" % sprayV  % "test",
      "com.typesafe.akka"       %%  "akka-remote"   % akkaV,
      "com.typesafe.akka"       %%  "akka-actor"    % akkaV,
      "com.typesafe.akka"       %%  "akka-testkit"  % akkaV   % "test",
      "org.specs2"              %%  "specs2-core"   % "2.3.11" % "test",
      *
      */
       /* spark for distributed processing
       */
      "org.apache.spark"        %% "spark-core" % "1.5.2",
      "org.apache.spark"        %% "spark-mllib" % "1.5.2",
      /*
       * breeze is the numerical processing lib used by spark
       * it is automatically included as a dependency to spark-mllib
      "org.biojava"             %  "biojava3-core" % "3.1.0"
       */
      "org.biojava"             % "core"    % "1.9.1",
      "edu.ucar"                % "netcdf4" % "4.5.5",
      "edu.ucar"                % "cdm"     % "4.5.5",
      "org.postgresql"          % "postgresql"  % "9.4-1204-jdbc42",
      "org.apache.commons"      % "commons-csv" % "1.2",
      "commons-cli"             % "commons-cli" % "1.3.1",
      /* These geometry libraries are used for structural feature
       * detection and attribute extraction. */
      "com.dreizak" % "miniball" % "1.0.3",
      /* These classes are used by the patent extractor package, and were copied over
       * from the original patent-extractor pom file (for maven).*/
      "commons-codec" % "commons-codec" % "1.10",
      "edu.emory.clir" % "clearnlp" % "3.2.0",
      "edu.emory.clir" % "clearnlp-dictionary" % "3.2",
      "org.apache.logging.log4j" % "log4j-api" % "2.3",
      "org.apache.logging.log4j" % "log4j-core" % "2.3",
      "org.apache.lucene" % "lucene-analyzers-common" % "5.2.1",
      "org.apache.lucene" % "lucene-core" % "5.2.1",
      "org.apache.lucene" % "lucene-queries" % "5.2.1",
      "org.apache.lucene" % "lucene-queryparser" % "5.2.1",
      "com.fasterxml.jackson.core" % "jackson-annotations" % "2.6.0",
      "com.fasterxml.jackson.core" % "jackson-core" % "2.6.0",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.0",
      /* Explicit versioning of jackson-module-scala is required for Spark 1.5.2. to work.
       * See https://github.com/FasterXML/jackson-module-scala/issues/214#issuecomment-188959382 */
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.0",
      "org.jsoup" % "jsoup" % "1.8.2",
      "uk.ac.cam.ch.wwmm" % "chemicalTagger" % "1.4.0",
      "uk.ac.cam.ch.wwmm.oscar" % "oscar4-api" % "4.2.2",
      "org.apache.commons" % "commons-collections4" % "4.1",
      "org.apache.httpcomponents" % "httpclient" % "4.5.2",
      "org.scalactic" %% "scalactic" % "3.0.0-RC4",
      "jaxen" % "jaxen" % "1.1.6",
      "org.eclipse.rdf4j" % "rdf4j-rio-api" % "2.0M3",
      "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % "2.0M3",
  /*
   * the maven repo jar seem to be outdated, or incompatible.
   * we posted to the indigo group bugs. The current resolution
   * is to use unmanaged jars we have locally. Soft link
   * lib/indigo{-inchi.jar, -renderer.jar, .jar} and lib/jna.jar
   *            , "com.ggasoftware" % "indigo" % "1.1.12"
   *            , "com.ggasoftware.indigo" % "indigo-renderer" % "1.1.12"
   *            , "com.ggasoftware.indigo" % "indigo-inchi" % "1.1.12"
  */
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "org.mockito" % "mockito-core" % "1.10.19" % "test",
      "org.powermock" % "powermock" % "1.6.4" % "test",
      "org.scalatest" %% "scalatest" % "3.0.0-RC4" % "test"
     )
}

Revolver.settings

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("com", "esotericsoftware", "minlog", xs)        => if (xs.startsWith("Log")) MergeStrategy.last else MergeStrategy.deduplicate
    case PathList("javax", "annotation", "meta", "When.class")    => MergeStrategy.first
    case PathList("com", "sun", "jna", xs @ _*)                   => MergeStrategy.first
    case PathList("javax", "xml", "namespace", xs @ _*)           => MergeStrategy.first
    case PathList("javax", "xml", xs @ _*)                        => MergeStrategy.last
    case PathList("javax", "servlet", xs @ _*)                    => MergeStrategy.first
    case PathList("org", "apache", "commons", "lang", xs @ _*)    => MergeStrategy.first
    case PathList("org", "apache", "commons", "logging", xs @ _*) => MergeStrategy.first
    case PathList("org", "apache", "commons", "codec", xs @ _*)   => MergeStrategy.last
    case PathList("org", "apache", "commons", "lang3", xs @ _*)   => MergeStrategy.last
    case PathList("org", "apache", "commons", "dbcp", xs @ _*)    => MergeStrategy.last
    case PathList("org", "apache", "commons", "jocl", xs @ _*)    => MergeStrategy.last
    case PathList("org", "apache", "commons", "pool", xs @ _*)    => MergeStrategy.last
    case PathList("org", "apache", "tools", xs @ _*)              => MergeStrategy.last
    case PathList("org", "apache", "http", xs @ _*)               => MergeStrategy.last
    case PathList("org", "eclipse", "jetty", xs @ _*)             => MergeStrategy.last
    case PathList("org", "xmlpull", "v1", xs @ _*)                => MergeStrategy.first
    case PathList("com", "beust", "jcommander", xs @ _*)          => MergeStrategy.last
    case PathList("com", "google", "common", xs @ _*)             => MergeStrategy.last
    case PathList("com", "google", "thirdparty", xs @ _*)         => MergeStrategy.last
    case PathList("org", "apache", "xmlcommons",  xs @ _*)        => MergeStrategy.last
    case PathList("org", "jgrapht", xs @ _*)                      => MergeStrategy.last
    case PathList("org", "jvnet", "mimepull", xs @ _*)            => MergeStrategy.last
    case PathList("org", "w3c", "dom", xs @ _*)                   => MergeStrategy.last
    case PathList("org", "xml", "sax", xs @ _*)                   => MergeStrategy.last
    case PathList("com", "mysql", xs @ _*)                        => MergeStrategy.last
    case PathList("org", "postgresql", xs @ _*)                   => MergeStrategy.last
    case PathList("javax", "annotation", xs @ _*)                 => MergeStrategy.last
    case PathList("oracle", "sql", xs @ _*)                       => MergeStrategy.last
    case PathList("org", "gjt", xs @ _*)                          => MergeStrategy.last
    case PathList("org", "hsqldb", xs @ _*)                       => MergeStrategy.last
    case PathList("sqlj", "runtime", xs @ _*)                     => MergeStrategy.last
    case PathList("org", "junit", xs @ _*)                        => MergeStrategy.last
    case PathList("net", "sf", "jniinchi", xs @ _*)               => MergeStrategy.first
    case PathList("nu", "xom", xs @ _*)                           => MergeStrategy.first
    case PathList("com", "fasterxml", "jackson", "core", xs @_ *) => MergeStrategy.first
    case PathList("org", "apache", "commons", "compress", xs @_ *) => MergeStrategy.first
    case PathList("org", "apache", "spark", "unused", xs @_ *)    => MergeStrategy.discard
    /*
     * When we add spark-mllib dependency, we get many additional pulls
     * conflict between spire_2.10/jars/spire_2.10-0.7.1.jar
     * and              spire-macros_2.10/jars/spire-macros_2.10-0.7.1.jar
     * resolved by taking the last
     */
    case PathList("scala", "reflect", "api", xs @ _*)              => MergeStrategy.last
    /*
     * This is a hack to accomodate the fact that the Spark jar includes
     * AKKA v2.2.6. See comment in libDependencies for the
     * io.spray/com.typesafe.akka that occur
     * BEFORE the spark include. That way when we package the spark
     * assembly and choose "last" as the strategy below, only the
     * spark jars are picked
     */
    case PathList("akka", xs @ _*)                                => MergeStrategy.last
    case PathList("chemaxon", "calculator", xs @ _*) => MergeStrategy.last
    case PathList("META-INF", "services", xs @ _*) => MergeStrategy.last
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("mailcap" :: Nil) | ("eclipsef.rsa" :: Nil) | ("eclipsef.sf" :: Nil) |
              ("index.list" :: Nil) | ("manifest.mf" :: Nil) |
              ("eclipse.inf" :: Nil) | ("dependencies" :: Nil) |
              ("license" :: Nil) | ("license.txt" :: Nil) |
              ("notice" :: Nil) | ("notice.txt" :: Nil) | ("license.apache2" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith("pom.properties") | ps.last.endsWith("pom.xml") =>
          MergeStrategy.discard
        /* With help from
         * http://stackoverflow.com/questions/999489/invalid-signature-file-when-attempting-to-run-a-jar */
        case ps @ (x :: xs) if ps.last.endsWith(".sf") | ps.last.endsWith(".dsa") | ps.last.endsWith(".rsa3")=>
          MergeStrategy.discard
        case _ => MergeStrategy.deduplicate
      }
    case "plugin.properties" => MergeStrategy.discard
    case "testpool.jocl" => MergeStrategy.last
    case "log4j.properties" => MergeStrategy.first
    case "log4j2.xml" => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".dll" => MergeStrategy.last
    case PathList(ps @ _*) if ps.last endsWith ".so" => MergeStrategy.last
    case PathList(ps @ _*) if ps.last == "package-info.class" => MergeStrategy.first
    case x => old(x)
  }
}
