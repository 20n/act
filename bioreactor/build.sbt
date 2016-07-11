import AssemblyKeys._

assemblySettings

name := "bioreactor"

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

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
        "commons-logging"       % "commons-logging" % "1.1.1"
      , "commons-discovery"     % "commons-discovery" % "0.2"
      , "commons-configuration" % "commons-configuration" % "1.10"
      , "commons-lang"          % "commons-lang" % "2.6"
      , "org.apache.commons"    % "commons-lang3" % "3.3.2"
      , "org.apache.commons"    % "commons-csv" % "1.2"
      , "commons-cli"           % "commons-cli" % "1.3.1"
      , "commons-codec" % "commons-codec" % "1.10"
      , "org.apache.logging.log4j" % "log4j-api" % "2.3"
      , "org.apache.logging.log4j" % "log4j-core" % "2.3"
      , "com.fasterxml.jackson.core" % "jackson-annotations" % "2.6.0"
      , "com.fasterxml.jackson.core" % "jackson-core" % "2.6.0"
      , "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.0"
      , "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.0.1"
      , "org.apache.commons" % "commons-collections4" % "4.1"
      , "com.novocode" % "junit-interface" % "0.11" % "test"
      , "org.mockito" % "mockito-core" % "1.10.19" % "test"
      , "org.powermock" % "powermock" % "1.6.4" % "test"
      , "joda-time" % "joda-time" % "2.9.4"
     )
}

Revolver.settings

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("javax", "annotation", "meta", "When.class")    => MergeStrategy.first
    case PathList("javax", "xml", "namespace", xs @ _*)           => MergeStrategy.first
    case PathList("javax", "xml", xs @ _*)                        => MergeStrategy.last
    case PathList("org", "apache", "commons", "lang", xs @ _*)    => MergeStrategy.first
    case PathList("org", "apache", "commons", "logging", xs @ _*) => MergeStrategy.first
    case PathList("org", "apache", "commons", "codec", xs @ _*)   => MergeStrategy.last
    case PathList("org", "apache", "commons", "lang3", xs @ _*)   => MergeStrategy.last
    case PathList("org", "apache", "commons", "dbcp", xs @ _*)    => MergeStrategy.last
    case PathList("org", "apache", "commons", "jocl", xs @ _*)    => MergeStrategy.last
    case PathList("org", "apache", "commons", "pool", xs @ _*)    => MergeStrategy.last
    case PathList("org", "apache", "tools", xs @ _*)              => MergeStrategy.last
    case PathList("org", "w3c", "dom", xs @ _*)                   => MergeStrategy.last
    case PathList("org", "xml", "sax", xs @ _*)                   => MergeStrategy.last
    case PathList("javax", "annotation", xs @ _*)                 => MergeStrategy.last
    case PathList("org", "junit", xs @ _*)                        => MergeStrategy.last
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
    case PathList(ps @ _*) if ps.last endsWith ".dll" => MergeStrategy.last
    case PathList(ps @ _*) if ps.last endsWith ".so" => MergeStrategy.last
    case x => old(x)
  }
}
