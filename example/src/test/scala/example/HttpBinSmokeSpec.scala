package example

import cats.effect.IO
import cats.effect.Resource
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import smithy4s.ShapeId
import smithy4s.Service
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.kinds.FunctorAlgebra
import smithy4s.tests.{DynamicSmokeTestRunner, SmokeTest, SmokeTestResult}
import software.amazon.smithy.model.Model
import weaver.IOSuite

/** Exercises smoke tests against httpbin.org's /status/{code} endpoint to test various HTTP error
  * codes. The model is loaded dynamically — no codegen needed.
  */
object HttpBinSmokeSpec extends IOSuite {

  private val smithyModel = """
    |$version: "2.0"
    |
    |namespace dyn.httpbin
    |
    |use smithy.test#smokeTests
    |use alloy#simpleRestJson
    |
    |@simpleRestJson
    |service HttpBinService {
    |    operations: [GetStatus]
    |}
    |
    |@smokeTests([
    |    {
    |        id: "StatusOk"
    |        params: { code: 200 }
    |        expect: { success: {} }
    |    },
    |    {
    |        id: "StatusNotFound"
    |        params: { code: 404 }
    |        expect: { failure: { errorId: NotFoundError } }
    |        tags: ["negative"]
    |    },
    |    {
    |        id: "StatusServerError"
    |        params: { code: 500 }
    |        expect: { failure: { errorId: InternalServerError } }
    |        tags: ["negative"]
    |    },
    |    {
    |        id: "StatusForbidden"
    |        params: { code: 403 }
    |        expect: { failure: { errorId: ForbiddenError } }
    |        tags: ["negative"]
    |    }
    |])
    |@http(method: "GET", uri: "/status/{code}", code: 200)
    |@readonly
    |operation GetStatus {
    |    input := {
    |        @required
    |        @httpLabel
    |        code: Integer
    |    }
    |    errors: [NotFoundError, InternalServerError, ForbiddenError]
    |}
    |
    |@error("client")
    |@httpError(404)
    |structure NotFoundError {}
    |
    |@error("server")
    |@httpError(500)
    |structure InternalServerError {}
    |
    |@error("client")
    |@httpError(403)
    |structure ForbiddenError {}
    |""".stripMargin

  private def loadDynamic: IO[DynamicSchemaIndex] =
    IO.blocking {
      val model = Model
        .assembler()
        .addUnparsedModel("httpbin.smithy", smithyModel)
        .discoverModels()
        .assemble()
        .unwrap()
      DynamicSchemaIndex.loadModel(model)
    }

  type Res = List[SmokeTest[IO]]

  def sharedResource: Resource[IO, Res] =
    EmberClientBuilder
      .default[IO]
      .build
      .evalMap { httpClient =>
        loadDynamic.flatMap { index =>
          IO.fromOption(index.getService(ShapeId("dyn.httpbin", "HttpBinService")))(
            new NoSuchElementException("Service not found in dynamic index")
          ).map { wrapper =>
            val makeImpl = new DynamicSmokeTestRunner.MakeImpl[IO] {
              def apply[Alg[_[_, _, _, _, _]]](
                  service: Service[Alg]
              ): Option[FunctorAlgebra[Alg, IO]] =
                SimpleRestJsonBuilder(service)
                  .client(httpClient)
                  .uri(Uri.unsafeFromString("https://httpbin.org"))
                  .make
                  .toOption
            }

            DynamicSmokeTestRunner.tests(wrapper, makeImpl)
          }
        }
      }

  test("httpbin smoke tests are extracted") { tests =>
    IO.pure(expect(tests.size == 4))
  }

  test("StatusOk - 200 returns success") { tests =>
    tests.find(_.id == "StatusOk") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }

  test("StatusNotFound - 404 returns failure") { tests =>
    tests.find(_.id == "StatusNotFound") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }

  test("StatusServerError - 500 returns failure") { tests =>
    tests.find(_.id == "StatusServerError") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }

  test("StatusForbidden - 403 returns failure") { tests =>
    tests.find(_.id == "StatusForbidden") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }
}
