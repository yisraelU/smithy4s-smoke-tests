package example

import cats.effect.IO
import smithy4s.Service
import smithy4s.ShapeId
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.kinds.FunctorAlgebra
import smithy4s.tests.{DynamicSmokeTestRunner, SmokeTest}
import software.amazon.smithy.model.Model
import weaver.SimpleIOSuite

/** Demonstrates loading a Smithy model dynamically at runtime (no codegen) and extracting smoke
  * tests from it using a stub implementation.
  */
object DynamicSmokeSpec extends SimpleIOSuite {

  private val smithyModel = """
    |$version: "2.0"
    |
    |namespace dynamic.example
    |
    |use smithy.test#smokeTests
    |
    |service GreetingService {
    |    operations: [Greet]
    |}
    |
    |@smokeTests([
    |    {
    |        id: "GreetSuccess"
    |        params: { name: "World" }
    |        expect: { success: {} }
    |    },
    |    {
    |        id: "GreetEmpty"
    |        params: { name: "" }
    |        expect: {
    |            failure: {}
    |        }
    |        tags: ["negative"]
    |    }
    |])
    |operation Greet {
    |    input := {
    |        @required
    |        name: String
    |    }
    |    output := {
    |        @required
    |        message: String
    |    }
    |}
    |""".stripMargin

  private def loadDynamic: IO[DynamicSchemaIndex] =
    IO.blocking {
      val model = Model
        .assembler()
        .addUnparsedModel("dynamic.smithy", smithyModel)
        .discoverModels()
        .assemble()
        .unwrap()
      DynamicSchemaIndex.loadModel(model)
    }

  /** Stub impl that always raises — used only for extracting test metadata. */
  private val stubImpl = new DynamicSmokeTestRunner.MakeImpl[IO] {
    def apply[Alg[_[_, _, _, _, _]]](
        service: Service[Alg]
    ): Option[FunctorAlgebra[Alg, IO]] =
      Some(
        service.fromPolyFunction[smithy4s.kinds.Kind1[IO]#toKind5](
          new smithy4s.kinds.PolyFunction5[service.Operation, smithy4s.kinds.Kind1[IO]#toKind5] {
            def apply[I, E, O, SI, SO](op: service.Operation[I, E, O, SI, SO]): IO[O] =
              IO.raiseError(new RuntimeException("stub - not a real impl"))
          }
        )
      )
  }

  private def loadTests: IO[List[SmokeTest[IO]]] =
    loadDynamic.flatMap { index =>
      IO.fromOption(index.getService(ShapeId("dynamic.example", "GreetingService")))(
        new NoSuchElementException("Service not found")
      ).map(wrapper => DynamicSmokeTestRunner.tests(wrapper, stubImpl))
    }

  test("dynamically loaded model exposes smoke tests") {
    loadTests.map { tests =>
      expect(tests.size == 2) and
        expect(tests.exists(_.id == "GreetSuccess")) and
        expect(tests.exists(_.id == "GreetEmpty"))
    }
  }

  test("smoke test metadata is correctly extracted") {
    loadTests.map { tests =>
      val greetSuccess = tests.find(_.id == "GreetSuccess").get
      val greetEmpty = tests.find(_.id == "GreetEmpty").get

      expect(greetSuccess.serviceId == ShapeId("dynamic.example", "GreetingService")) and
        expect(greetSuccess.operationId == ShapeId("dynamic.example", "Greet")) and
        expect(greetSuccess.tags.isEmpty) and
        expect(greetEmpty.tags == List("negative"))
    }
  }
}
