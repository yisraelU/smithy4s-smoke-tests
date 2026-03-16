package smithy4s.tests.cli

import cats.effect.IO
import cats.syntax.all._
import smithy.test.{Expectation, SmokeTests}
import smithy4s.dynamic.DynamicSchemaIndex

object ListCommand {

  def run(
      index: DynamicSchemaIndex,
      serviceFilter: Option[String],
      tagFilter: Option[String]
  ): IO[Unit] = {
    val entries = for {
      wrapper <- ModelLoader.filteredWrappers(index, serviceFilter)
      ep <- wrapper.service.endpoints.toList
      smokeTests <- ep.hints.get(SmokeTests).toList
      tc <- smokeTests.value
      tags = tc.tags.map(_.map(_.value)).getOrElse(Nil)
      if tagFilter.forall(tags.contains)
    } yield (wrapper.service.id, ep.id, tc, tags)

    if (entries.isEmpty) IO.println("No smoke tests found.")
    else
      IO.println(s"Found ${entries.size} smoke test(s):\n") *>
        entries.traverse_ { case (serviceId, opId, tc, tags) =>
          val expect = tc.expect match {
            case Expectation.SuccessCase => "success"
            case Expectation.FailureCase(f) =>
              f.errorId.fold("failure")(id => s"failure(${id.show})")
          }
          val tagStr = if (tags.isEmpty) "" else s" [${tags.mkString(", ")}]"
          IO.println(s"  ${serviceId.name}#${opId.name} - ${tc.id} (expect: $expect)$tagStr")
        }
  }
}
