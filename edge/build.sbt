organization  := "com.act"

version       := "0.1"

scalaVersion  := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

// ******************************************************************** 
// README
// 
// The backend uses Spark (build.sbt under reachables/ or backend/)
// The Spark jars include within them akka @ version 2.2.6. Therefore
// having the edge/ code and the backend/ | reachables/ code be under
// the build.sbt is not possible. 
// 
// We need the following to work: 
// - sbt assembly (creating the fat jar for Spark EC2 runs)
// - sbt re-start (for starting the edge REST service)
// We cannot get both of them to work simultaneously because of the
// versioning conflict.
// 
// Maybe when spark gets updated to Akka 2.3.6 the conflict will magically
// disappear. Until then, we have to keep the edge/ code under this 
// different build.sbt
// ******************************************************************** 

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-caching" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test"
  )
}

Revolver.settings
