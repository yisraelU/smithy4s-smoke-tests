package smithy4s.tests

sealed trait SmokeTestResult extends Product with Serializable
object SmokeTestResult {
  case object Pass extends SmokeTestResult
  case class Fail(message: String, cause: Option[Throwable] = None) extends SmokeTestResult
}
