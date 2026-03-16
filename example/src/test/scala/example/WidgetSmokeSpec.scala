package example

import cats.effect.IO
import example.widget._
import smithy4s.tests.{SmokeTestRunner, SmokeTestResult}
import weaver.SimpleIOSuite

object WidgetSmokeSpec extends SimpleIOSuite {

  private val successImpl: WidgetService[IO] = new WidgetService[IO] {
    def getWidget(id: String): IO[GetWidgetOutput] =
      if (id == "does-not-exist")
        IO.raiseError(WidgetNotFoundError(s"Widget $id not found"))
      else
        IO.pure(GetWidgetOutput(id = id, name = s"Widget $id"))
  }

  private val tests = SmokeTestRunner.tests(WidgetService, successImpl)

  test("codegen smoke tests are extracted") {
    IO.pure(expect(tests.size == 2))
  }

  test("GetWidgetSuccess") {
    tests.find(_.id == "GetWidgetSuccess") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }

  test("GetWidgetNotFound - expects specific errorId") {
    tests.find(_.id == "GetWidgetNotFound") match {
      case Some(tc) =>
        tc.run.map {
          case SmokeTestResult.Pass       => expect(true)
          case SmokeTestResult.Fail(m, _) => failure(m)
        }
      case None => IO.pure(failure("test not found"))
    }
  }
}
