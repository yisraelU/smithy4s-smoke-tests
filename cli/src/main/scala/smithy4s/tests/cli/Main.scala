package smithy4s.tests.cli

import cats.effect._
import cats.syntax.all._
import com.monovore.decline._
import com.monovore.decline.effect._

object Main
    extends CommandIOApp(
      name = "smithy4s-smoke-test",
      header = "Run Smithy smoke tests against a live service, or list discovered tests."
    ) {

  private val listCmd = Opts.subcommand("list", "List smoke tests found in a Smithy model.") {
    (CliOpts.modelSource, CliOpts.service, CliOpts.tag).mapN { (source, service, tag) =>
      ModelLoader.load(source).flatMap(ListCommand.run(_, service, tag))
    }
  }

  private val runCmd = Opts.subcommand("run", "Execute smoke tests against a live HTTP service.") {
    (CliOpts.modelSource, CliOpts.url, CliOpts.service, CliOpts.tag).mapN {
      (source, url, service, tag) =>
        ModelLoader.load(source).flatMap(RunCommand.run(_, url, service, tag))
    }
  }

  def main: Opts[IO[ExitCode]] =
    (listCmd orElse runCmd).map { action =>
      action
        .as(ExitCode.Success)
        .handleErrorWith { err =>
          IO.consoleForIO.errorln(s"Error: ${err.getMessage}").as(ExitCode.Error)
        }
    }
}
