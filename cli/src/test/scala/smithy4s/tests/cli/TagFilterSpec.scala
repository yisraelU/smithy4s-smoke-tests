package smithy4s.tests.cli

import cats.effect.IO
import smithy4s.Service
import smithy4s.ShapeId
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.kinds.FunctorAlgebra
import smithy4s.tests.{DynamicSmokeTestRunner, SmokeTest}
import software.amazon.smithy.model.Model
import weaver.SimpleIOSuite

object TagFilterSpec extends SimpleIOSuite {

  private val smithyModel = """
    |$version: "2.0"
    |
    |namespace filter.example
    |
    |use smithy.test#smokeTests
    |
    |service TestService {
    |    operations: [OpA, OpB]
    |}
    |
    |@smokeTests([
    |    {
    |        id: "TaggedSmoke"
    |        params: { x: "hello" }
    |        expect: { success: {} }
    |        tags: ["smoke"]
    |    },
    |    {
    |        id: "TaggedNegative"
    |        params: { x: "" }
    |        expect: { failure: {} }
    |        tags: ["negative"]
    |    },
    |    {
    |        id: "Untagged"
    |        params: { x: "world" }
    |        expect: { success: {} }
    |    }
    |])
    |operation OpA {
    |    input := {
    |        @required
    |        x: String
    |    }
    |    output := {
    |        @required
    |        y: String
    |    }
    |}
    |
    |@smokeTests([
    |    {
    |        id: "OpBSmoke"
    |        params: { a: 1 }
    |        expect: { success: {} }
    |        tags: ["smoke", "integration"]
    |    }
    |])
    |operation OpB {
    |    input := {
    |        @required
    |        a: Integer
    |    }
    |    output := {
    |        @required
    |        b: Integer
    |    }
    |}
    |""".stripMargin

  private def loadIndex: IO[DynamicSchemaIndex] =
    IO.blocking {
      val model = Model
        .assembler()
        .addUnparsedModel("filter.smithy", smithyModel)
        .discoverModels()
        .assemble()
        .unwrap()
      DynamicSchemaIndex.loadModel(model)
    }

  private val stubImpl = new DynamicSmokeTestRunner.MakeImpl[IO] {
    def apply[Alg[_[_, _, _, _, _]]](
        service: Service[Alg]
    ): Option[FunctorAlgebra[Alg, IO]] =
      Some(
        service.fromPolyFunction[smithy4s.kinds.Kind1[IO]#toKind5](
          new smithy4s.kinds.PolyFunction5[service.Operation, smithy4s.kinds.Kind1[IO]#toKind5] {
            def apply[I, E, O, SI, SO](op: service.Operation[I, E, O, SI, SO]): IO[O] =
              IO.raiseError(new RuntimeException("stub"))
          }
        )
      )
  }

  private def loadTests: IO[List[SmokeTest[IO]]] =
    loadIndex.flatMap { index =>
      IO.fromOption(index.getService(ShapeId("filter.example", "TestService")))(
        new NoSuchElementException("Service not found")
      ).map(wrapper => DynamicSmokeTestRunner.tests(wrapper, stubImpl))
    }

  test("no tag filter returns all tests") {
    loadTests.map { tests =>
      val filtered = tests.filter(t => None.forall(tag => t.tags.contains(tag)))
      expect(filtered.size == 4)
    }
  }

  test("filter by 'smoke' tag returns only smoke-tagged tests") {
    loadTests.map { tests =>
      val tagFilter: Option[String] = Some("smoke")
      val filtered = tests.filter(t => tagFilter.forall(tag => t.tags.contains(tag)))
      expect(filtered.size == 2) and
        expect(filtered.map(_.id).toSet == Set("TaggedSmoke", "OpBSmoke"))
    }
  }

  test("filter by 'negative' tag returns only negative-tagged tests") {
    loadTests.map { tests =>
      val tagFilter: Option[String] = Some("negative")
      val filtered = tests.filter(t => tagFilter.forall(tag => t.tags.contains(tag)))
      expect(filtered.size == 1) and
        expect(filtered.head.id == "TaggedNegative")
    }
  }

  test("filter by 'integration' tag returns only integration-tagged tests") {
    loadTests.map { tests =>
      val tagFilter: Option[String] = Some("integration")
      val filtered = tests.filter(t => tagFilter.forall(tag => t.tags.contains(tag)))
      expect(filtered.size == 1) and
        expect(filtered.head.id == "OpBSmoke")
    }
  }

  test("filter by nonexistent tag returns empty") {
    loadTests.map { tests =>
      val tagFilter: Option[String] = Some("nonexistent")
      val filtered = tests.filter(t => tagFilter.forall(tag => t.tags.contains(tag)))
      expect(filtered.isEmpty)
    }
  }

  test("tags are correctly extracted from smoke test cases") {
    loadTests.map { tests =>
      val tagged = tests.find(_.id == "TaggedSmoke").get
      val untagged = tests.find(_.id == "Untagged").get
      val multi = tests.find(_.id == "OpBSmoke").get

      expect(tagged.tags == List("smoke")) and
        expect(untagged.tags.isEmpty) and
        expect(multi.tags == List("smoke", "integration"))
    }
  }
}
