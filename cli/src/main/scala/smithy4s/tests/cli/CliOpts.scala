package smithy4s.tests.cli

import cats.data.{Validated, ValidatedNel}
import cats.syntax.all._
import com.monovore.decline._
import fs2.io.file.Path
import org.http4s.Uri

object CliOpts {

  implicit val pathArgument: Argument[Path] = new Argument[Path] {
    def read(string: String): ValidatedNel[String, Path] = {
      val path = Path(string)
      if (java.nio.file.Files.exists(path.toNioPath))
        Validated.validNel(path)
      else
        Validated.invalidNel(s"File not found: $string")
    }
    def defaultMetavar: String = "path"
  }

  implicit val uriArgument: Argument[Uri] = new Argument[Uri] {
    def read(string: String): ValidatedNel[String, Uri] =
      Uri.fromString(string).leftMap(e => s"Invalid URI: ${e.message}").toValidatedNel
    def defaultMetavar: String = "uri"
  }

  val file: Opts[Path] =
    Opts.option[Path]("file", "Path to a Smithy model file.")

  val jar: Opts[List[Path]] =
    Opts.options[Path]("jar", "Path to a JAR containing Smithy models (repeatable).").map(_.toList)

  val modelSource: Opts[ModelSource] =
    file.map(ModelSource.File(_): ModelSource) orElse
      jar.map(ModelSource.Jar(_): ModelSource) orElse
      Opts(ModelSource.Stdin: ModelSource)

  val service: Opts[Option[String]] =
    Opts.option[String]("service", "Filter to a specific service by name.").orNone

  val url: Opts[Uri] =
    Opts.option[Uri]("url", "Base URL for the HTTP service.")

  val tag: Opts[Option[String]] =
    Opts.option[String]("tag", "Filter tests by tag.").orNone
}
