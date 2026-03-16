ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version := "0.1.0-SNAPSHOT"

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / scalafmtOnCompile := true

val projectPrefix = "smithy4s-smoke-tests"
val weaverTestFramework = new TestFramework("weaver.framework.CatsEffect")

lazy val core = (project in file("core"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := s"$projectPrefix-core",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core,
      Dependencies.CatsEffect.core,
      Dependencies.Smithy.smokeTestTraits % Smithy4s
    ),
    Compile / smithy4sAllowedNamespaces := List("smithy.test")
  )

lazy val dynamic = (project in file("dynamic"))
  .settings(
    name := s"$projectPrefix-dynamic",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.dynamic
    )
  )
  .dependsOn(core)

lazy val example = (project in file("example"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := s"$projectPrefix-example",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.http4s,
      Dependencies.Http4s.emberClient,
      Dependencies.Weaver.cats          % Test,
      Dependencies.Smithy.smokeTestTraits % Smithy4s,
      Dependencies.Smithy.smokeTestTraits % Test,
      Dependencies.Alloy.core           % Smithy4s,
      Dependencies.Alloy.core           % Test
    ),
    testFrameworks += weaverTestFramework
  )
  .dependsOn(core, dynamic)

lazy val cli = (project in file("cli"))
  .settings(
    name := s"$projectPrefix-cli",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.http4s,
      Dependencies.Http4s.emberClient,
      Dependencies.Smithy.smokeTestTraits,
      Dependencies.Alloy.core,
      Dependencies.Decline.effect,
      Dependencies.Weaver.cats % Test
    ),
    testFrameworks += weaverTestFramework
  )
  .dependsOn(dynamic)

lazy val docs = (project in file("docs-target"))
  .enablePlugins(MdocPlugin)
  .settings(
    name := s"$projectPrefix-docs",
    mdocIn := (ThisBuild / baseDirectory).value / "docs",
    mdocOut := (ThisBuild / baseDirectory).value,
    libraryDependencies ++= Seq(
      Dependencies.Weaver.cats
    ),
    publish / skip := true
  )
  .dependsOn(example, cli)

lazy val root = (project in file("."))
  .aggregate(core, dynamic, example, cli, docs)
  .settings(
    name := projectPrefix,
    publish / skip := true
  )
