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

/** Demonstrates running real HTTP smoke tests using a dynamically loaded Smithy model — zero
  * codegen required. The model is loaded at runtime, an HTTP client is created dynamically, and
  * smoke tests are extracted and executed against the live JSONPlaceholder API.
  */
object DynamicHttpSmokeSpec extends IOSuite {

  private val smithyModel = """
    |$version: "2.0"
    |
    |namespace dyn.jsonplaceholder
    |
    |use smithy.test#smokeTests
    |use alloy#simpleRestJson
    |
    |@simpleRestJson
    |service JsonPlaceholderService {
    |    operations: [GetPost]
    |}
    |
    |@smokeTests([
    |    {
    |        id: "DynGetPostSuccess"
    |        params: { id: 1 }
    |        expect: { success: {} }
    |    },
    |    {
    |        id: "DynGetPostNotFound"
    |        params: { id: 0 }
    |        expect: { failure: {} }
    |    }
    |])
    |@http(method: "GET", uri: "/posts/{id}", code: 200)
    |@readonly
    |operation GetPost {
    |    input := {
    |        @required
    |        @httpLabel
    |        id: Integer
    |    }
    |    output := {
    |        @required
    |        id: Integer
    |        @required
    |        userId: Integer
    |        @required
    |        title: String
    |        @required
    |        body: String
    |    }
    |}
    |""".stripMargin

  private def loadDynamic: IO[DynamicSchemaIndex] =
    IO.blocking {
      val model = Model
        .assembler()
        .addUnparsedModel("dynamic-http.smithy", smithyModel)
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
          IO.fromOption(index.getService(ShapeId("dyn.jsonplaceholder", "JsonPlaceholderService")))(
            new NoSuchElementException("Service not found in dynamic index")
          ).map { wrapper =>
            val makeImpl = new DynamicSmokeTestRunner.MakeImpl[IO] {
              def apply[Alg[_[_, _, _, _, _]]](
                  service: Service[Alg]
              ): Option[FunctorAlgebra[Alg, IO]] =
                SimpleRestJsonBuilder(service)
                  .client(httpClient)
                  .uri(Uri.unsafeFromString("https://jsonplaceholder.typicode.com"))
                  .make
                  .toOption
            }

            DynamicSmokeTestRunner.tests(wrapper, makeImpl)
          }
        }
      }

  test("dynamic HTTP smoke tests are extracted") { tests =>
    IO.pure(expect(tests.size == 2))
  }

  test("DynGetPostSuccess - dynamic HTTP call to live API") { tests =>
    tests.find(_.id == "DynGetPostSuccess") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }

  test("DynGetPostNotFound - dynamic HTTP call to live API") { tests =>
    tests.find(_.id == "DynGetPostNotFound") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }
}
