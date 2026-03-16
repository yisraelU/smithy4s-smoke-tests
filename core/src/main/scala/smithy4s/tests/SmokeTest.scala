package smithy4s.tests

import smithy4s.ShapeId

case class SmokeTest[F[_]](
    id: String,
    serviceId: ShapeId,
    operationId: ShapeId,
    tags: List[String],
    run: F[SmokeTestResult]
)
