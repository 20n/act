name := "reachables"

version := "0.1"

scalaVersion := "2.10.3"

resolvers += "PaxTools from BioPax.org" at "http://www.biopax.org/m2repo/releases/"

libraryDependencies ++= Seq( "org.mongodb" %% "casbah" % "2.7.1"
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
                          /* we get paxtools from the biopax resolver */
                          , "org.biopax.paxtools" % "paxtools" % "4.2.0"
                          , "org.biopax.paxtools" % "paxtools-core" % "4.2.0"
/*
 * the maven repo jar seem to be outdated, or incompatible. 
 * we posted to the indigo group bugs. The current resolution
 * is to use unmanaged jars we have locally. Soft link
 * lib/indigo{-inchi.jar, -renderer.jar, .jar} and lib/jna.jar
                          , "com.ggasoftware" % "indigo" % "1.1.12"
                          , "com.ggasoftware.indigo" % "indigo-renderer" % "1.1.12"
                          , "com.ggasoftware.indigo" % "indigo-inchi" % "1.1.12"
*/
                          )

