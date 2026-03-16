import sbt._
import smithy4s.codegen.BuildInfo as S4sBuildInfo

object Dependencies {

  object Versions {
    val smithy4s: String   = S4sBuildInfo.version
    val smithy: String     = S4sBuildInfo.smithyVersion
    val alloy: String      = S4sBuildInfo.alloyVersion
    val http4s     = "0.23.33"
    val catsEffect = "3.7.0"
    val weaver     = "0.8.4"
    val decline    = "2.6.0"
  }

  object Smithy4s {
    val core    = "com.disneystreaming.smithy4s" %% "smithy4s-core"    % Versions.smithy4s
    val dynamic = "com.disneystreaming.smithy4s" %% "smithy4s-dynamic" % Versions.smithy4s
    val http4s  = "com.disneystreaming.smithy4s" %% "smithy4s-http4s"  % Versions.smithy4s
  }

  object Smithy {
    val smokeTestTraits = "software.amazon.smithy" % "smithy-smoke-test-traits" % Versions.smithy
  }

  object Alloy {
    val core = S4sBuildInfo.alloyOrg % "alloy-core" % Versions.alloy
  }

  object Http4s {
    val emberClient = "org.http4s" %% "http4s-ember-client" % Versions.http4s
  }

  object CatsEffect {
    val core = "org.typelevel" %% "cats-effect" % Versions.catsEffect
  }

  object Decline {
    val effect = "com.monovore" %% "decline-effect" % Versions.decline
  }

  object Weaver {
    val cats = "com.disneystreaming" %% "weaver-cats" % Versions.weaver
  }
}
