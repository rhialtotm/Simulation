scalaVersion := "2.12.1"
scalaSource in Compile := baseDirectory.value / "src"
scalacOptions ++= Seq("-opt:_", "-target:jvm-1.8")
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "org.rogach" %% "scallop" % "2.0.6"
//libraryDependencies += "com.github.cb372" %% "scalacache-ehcache" % "0.9.3"
fork in run := true
cancelable in Global := true
javaOptions in run += "-Xmx2048M"
javaOptions in run += "-Xms2048M"

