package smithy4s.tests.cli

import cats.effect.IO
import smithy4s.ShapeId
import smithy4s.dynamic.DynamicSchemaIndex
import software.amazon.smithy.model.Model
import weaver.SimpleIOSuite

object ModelLoaderSpec extends SimpleIOSuite {

  private val smithyModel = """
    |$version: "2.0"
    |
    |namespace loader.example
    |
    |service AlphaService {
    |    operations: [AlphaOp]
    |}
    |
    |operation AlphaOp {
    |    input := { x: String }
    |    output := { y: String }
    |}
    |
    |service BetaService {
    |    operations: [BetaOp]
    |}
    |
    |operation BetaOp {
    |    input := { a: Integer }
    |    output := { b: Integer }
    |}
    |""".stripMargin

  private def loadIndex: IO[DynamicSchemaIndex] =
    IO.blocking {
      val model = Model
        .assembler()
        .addUnparsedModel("loader.smithy", smithyModel)
        .discoverModels()
        .assemble()
        .unwrap()
      DynamicSchemaIndex.loadModel(model)
    }

  test("filteredWrappers with no filter returns all services") {
    loadIndex.map { index =>
      val wrappers = ModelLoader.filteredWrappers(index, None)
      val names = wrappers.map(_.service.id.name).toSet
      expect(names.contains("AlphaService")) and
        expect(names.contains("BetaService"))
    }
  }

  test("filteredWrappers with name filter returns matching service") {
    loadIndex.map { index =>
      val wrappers = ModelLoader.filteredWrappers(index, Some("AlphaService"))
      expect(wrappers.size == 1) and
        expect(wrappers.head.service.id.name == "AlphaService")
    }
  }

  test("filteredWrappers with case-insensitive name filter") {
    loadIndex.map { index =>
      val wrappers = ModelLoader.filteredWrappers(index, Some("alphaservice"))
      expect(wrappers.size == 1) and
        expect(wrappers.head.service.id.name == "AlphaService")
    }
  }

  test("filteredWrappers with full ShapeId filter") {
    loadIndex.map { index =>
      val wrappers = ModelLoader.filteredWrappers(index, Some("loader.example#BetaService"))
      expect(wrappers.size == 1) and
        expect(wrappers.head.service.id == ShapeId("loader.example", "BetaService"))
    }
  }

  test("filteredWrappers with non-matching filter returns empty") {
    loadIndex.map { index =>
      val wrappers = ModelLoader.filteredWrappers(index, Some("NoSuchService"))
      expect(wrappers.isEmpty)
    }
  }
}
