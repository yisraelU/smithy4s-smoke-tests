package smithy4s.tests.cli

import cats.effect.IO
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import smithy4s.Service
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.kinds.FunctorAlgebra
import smithy4s.tests.{DynamicSmokeTestRunner, SmokeTest, SmokeTestResult}

object RunCommand {

  def run(
      index: DynamicSchemaIndex,
      baseUri: Uri,
      serviceFilter: Option[String],
      tagFilter: Option[String]
  ): IO[Unit] = {
    val wrappers = ModelLoader.filteredWrappers(index, serviceFilter)

    EmberClientBuilder
      .default[IO]
      .build
      .use { httpClient =>
        val makeImpl = new DynamicSmokeTestRunner.MakeImpl[IO] {
          def apply[Alg[_[_, _, _, _, _]]](
              service: Service[Alg]
          ): Option[FunctorAlgebra[Alg, IO]] =
            SimpleRestJsonBuilder(service)
              .client(httpClient)
              .uri(baseUri)
              .make
              .toOption
        }

        val tests = wrappers
          .flatMap { wrapper =>
            DynamicSmokeTestRunner.tests(wrapper, makeImpl)
          }
          .filter(t => tagFilter.forall(tag => t.tags.contains(tag)))

        if (tests.isEmpty) IO.println("No smoke tests found.")
        else execute(tests).flatMap(printReport)
      }
  }

  private def execute(tests: List[SmokeTest[IO]]): IO[Report] =
    tests
      .traverse { test =>
        test.run.map { result =>
          Report.Entry(
            id = test.id,
            serviceId = test.serviceId,
            operationId = test.operationId,
            tags = test.tags,
            result = result
          )
        }
      }
      .map(Report(_))

  private def printReport(report: Report): IO[Unit] =
    IO.println(s"Running ${report.total} smoke test(s)...\n") *>
      report.entries.traverse_ { entry =>
        val (status, detail) = entry.result match {
          case SmokeTestResult.Pass         => ("PASS", "")
          case SmokeTestResult.Fail(msg, _) => ("FAIL", s"\n    $msg")
        }
        val tagStr = if (entry.tags.isEmpty) "" else s" [${entry.tags.mkString(", ")}]"
        IO.println(
          s"  [$status] ${entry.serviceId.name}#${entry.operationId.name} - ${entry.id}$tagStr$detail"
        )
      } *>
      IO.println(
        s"\nResults: ${report.passed} passed, ${report.failed} failed, ${report.total} total"
      ) *>
      (if (report.failed > 0)
         IO.raiseError(new RuntimeException(s"${report.failed} test(s) failed"))
       else IO.unit)
}
