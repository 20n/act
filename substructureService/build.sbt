import AssemblyKeys._

assemblySettings

name := "substructureSearch"

/*
  This option allows us to run Spark through the job manager.
  If we did not use this option, Spark will not able to correctly establish a class path.
 */
/* fork := true */

version := "0.1"

scalaVersion := "2.10.3"

parallelExecution in Test := false

resolvers ++= {
  Seq(
      "spring-releases" at "https://repo.spring.io/libs-release"
     )
}

/* To disable tests during assembly, add this directive: `test in assembly := {}` */
libraryDependencies ++= {
  Seq(
      "commons-lang"          % "commons-lang" % "2.6",
      "org.apache.commons"    % "commons-lang3" % "3.3.2",
      "org.apache.commons"      % "commons-csv" % "1.2",
      "commons-cli"             % "commons-cli" % "1.3.1",
      "commons-codec" % "commons-codec" % "1.10",
      "org.apache.logging.log4j" % "log4j-api" % "2.3",
      "org.apache.logging.log4j" % "log4j-core" % "2.3",
      "com.fasterxml.jackson.core" % "jackson-annotations" % "2.6.0",
      "com.fasterxml.jackson.core" % "jackson-core" % "2.6.0",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.0",
      /* Explicit versioning of jackson-module-scala is required for Spark 1.5.2. to work.
       * See https://github.com/FasterXML/jackson-module-scala/issues/214#issuecomment-188959382 */
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.0",
      "org.apache.commons" % "commons-collections4" % "4.1",
      "org.apache.httpcomponents" % "httpclient" % "4.5.2",
      "org.eclipse.jetty" % "jetty-server" % "9.4.0.v20161208",
      "org.eclipse.jetty" % "jetty-servlet" % "9.4.0.v20161208",
      "org.eclipse.jetty" % "jetty-util" % "9.4.0.v20161208",
      /* Test modules go last. */
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "org.mockito" % "mockito-core" % "1.10.19" % "test",
      "org.powermock" % "powermock" % "1.6.4" % "test",
      "org.scalatest" %% "scalatest" % "3.0.0-RC4" % "test",
      "org.apache.maven.plugins" % "maven-surefire-report-plugin" % "2.17" % "test"
     )
}

// We need the following for platform specific native Z3 libraries
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
