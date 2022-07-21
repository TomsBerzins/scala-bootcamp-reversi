val Http4sVersion = "0.22.13"
val CirceVersion = "0.14.2"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.2.10"
val MunitCatsEffectVersion = "1.0.7"

lazy val root = (project in file("."))
  .settings(
    organization := "lv.tomsberzins",
    name := "reversi",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.8",
    scalacOptions ++= Seq(
      "-Ymacro-annotations",
      "-Ywarn-value-discard",
      "-Xfatal-warnings"
    ),
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-blaze-server" %  Http4sVersion,
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "io.circe"        %% "circe-parser"        % CirceVersion,
      "org.typelevel"   %% "cats-core"           % "2.7.0",
      "org.typelevel"   %% "cats-effect"         % "2.5.5",
      "org.scalatest"   %% "scalatest"           % "3.2.12" % Test,
      "com.codecommit" %% "cats-effect-testing-scalatest" % "0.5.4" % Test,
      "org.scalatestplus" %% "mockito-4-5" % "3.2.12.0" % Test,
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion         % Runtime,
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework")
  )
