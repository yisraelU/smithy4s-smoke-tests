package smithy4s.tests.cli

import smithy4s.ShapeId
import smithy4s.tests.SmokeTestResult

case class Report(entries: List[Report.Entry]) {
  val passed: Int = entries.count(_.result == SmokeTestResult.Pass)
  val failed: Int = entries.size - passed
  val total: Int = entries.size
}

object Report {
  case class Entry(
      id: String,
      serviceId: ShapeId,
      operationId: ShapeId,
      tags: List[String],
      result: SmokeTestResult
  )

  val empty: Report = Report(Nil)
}
