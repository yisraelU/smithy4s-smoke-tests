package smithy4s.tests.cli

import cats.effect.IO
import smithy4s.ShapeId
import smithy4s.tests.SmokeTestResult
import weaver.SimpleIOSuite

object ReportSpec extends SimpleIOSuite {

  private val serviceId = ShapeId("example", "MyService")
  private val opId = ShapeId("example", "MyOp")

  private def entry(
      id: String,
      tags: List[String] = Nil,
      result: SmokeTestResult = SmokeTestResult.Pass
  ): Report.Entry =
    Report.Entry(id = id, serviceId = serviceId, operationId = opId, tags = tags, result = result)

  test("empty report has zero counts") {
    val report = Report.empty
    IO.pure(
      expect(report.passed == 0) and
        expect(report.failed == 0) and
        expect(report.total == 0)
    )
  }

  test("counts passing and failing entries") {
    val report = Report(
      List(
        entry("t1"),
        entry("t2", result = SmokeTestResult.Fail("boom")),
        entry("t3"),
        entry("t4", result = SmokeTestResult.Fail("bang"))
      )
    )
    IO.pure(
      expect(report.passed == 2) and
        expect(report.failed == 2) and
        expect(report.total == 4)
    )
  }

  test("all passing") {
    val report = Report(List(entry("t1"), entry("t2"), entry("t3")))
    IO.pure(
      expect(report.passed == 3) and
        expect(report.failed == 0) and
        expect(report.total == 3)
    )
  }

  test("all failing") {
    val report = Report(
      List(
        entry("t1", result = SmokeTestResult.Fail("a")),
        entry("t2", result = SmokeTestResult.Fail("b"))
      )
    )
    IO.pure(
      expect(report.passed == 0) and
        expect(report.failed == 2) and
        expect(report.total == 2)
    )
  }

  test("tags are preserved in entries") {
    val report = Report(
      List(
        entry("t1", tags = List("smoke", "negative")),
        entry("t2")
      )
    )
    IO.pure(
      expect(report.entries.head.tags == List("smoke", "negative")) and
        expect(report.entries(1).tags.isEmpty)
    )
  }
}
