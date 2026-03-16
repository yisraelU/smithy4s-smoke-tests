package smithy4s.tests.cli

import cats.effect.IO
import fs2.io.file.{Files => Fs2Files, Path}
import smithy4s.dynamic.DynamicSchemaIndex
import software.amazon.smithy.model.Model

sealed trait ModelSource
object ModelSource {
  case class File(path: Path) extends ModelSource
  case class Jar(paths: List[Path]) extends ModelSource
  case object Stdin extends ModelSource
}

object ModelLoader {

  def load(source: ModelSource): IO[DynamicSchemaIndex] =
    source match {
      case ModelSource.File(path) =>
        readFile(path).flatMap(text => assemble(text))
      case ModelSource.Jar(paths) =>
        assembleJars(paths)
      case ModelSource.Stdin =>
        readStdin.flatMap(text => assemble(text))
    }

  def filteredWrappers(
      index: DynamicSchemaIndex,
      serviceFilter: Option[String]
  ): List[DynamicSchemaIndex.ServiceWrapper] =
    index.allServices.toList.filter { wrapper =>
      serviceFilter.forall { filter =>
        wrapper.service.id.name.equalsIgnoreCase(filter) ||
        wrapper.service.id.toString == filter
      }
    }

  private def readFile(path: Path): IO[String] =
    Fs2Files[IO].readUtf8(path).compile.string

  private def readStdin: IO[String] =
    fs2.io
      .stdinUtf8[IO](8192)
      .through(fs2.text.lines)
      .intersperse("\n")
      .compile
      .string

  private def assemble(smithyText: String): IO[DynamicSchemaIndex] =
    IO.blocking {
      val model = Model
        .assembler()
        .addUnparsedModel("input.smithy", smithyText)
        .discoverModels()
        .assemble()
        .unwrap()
      DynamicSchemaIndex.loadModel(model)
    }

  private def assembleJars(paths: List[Path]): IO[DynamicSchemaIndex] =
    IO.blocking {
      val assembler = paths.foldLeft(Model.assembler()) { (a, jar) =>
        a.addImport(jar.toNioPath)
      }
      val model = assembler
        .discoverModels()
        .assemble()
        .unwrap()
      DynamicSchemaIndex.loadModel(model)
    }
}
