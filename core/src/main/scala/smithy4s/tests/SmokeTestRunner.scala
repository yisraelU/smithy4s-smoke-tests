package smithy4s.tests

import cats.MonadThrow
import cats.syntax.all._
import smithy.test._
import smithy4s.{Document, Service, ShapeId}
import smithy4s.kinds.{FunctorAlgebra, Kind1}
import smithy4s.schema.ErrorSchema

object SmokeTestRunner {

  def tests[F[_], Alg[_[_, _, _, _, _]]](
      service: Service[Alg],
      impl: FunctorAlgebra[Alg, F]
  )(implicit F: MonadThrow[F]): List[SmokeTest[F]] = {
    val poly = service.toPolyFunction[Kind1[F]#toKind5](impl)
    service.endpoints.toList.flatMap(ep => extractTests(service.id, ep, poly))
  }

  private def extractTests[F[_], Op[_, _, _, _, _], I, E, O, SI, SO](
      serviceId: ShapeId,
      endpoint: smithy4s.Endpoint[Op, I, E, O, SI, SO],
      poly: smithy4s.kinds.PolyFunction5[Op, Kind1[F]#toKind5]
  )(implicit F: MonadThrow[F]): List[SmokeTest[F]] = {
    val smokeTests = endpoint.hints
      .get(SmokeTests)
      .map(_.value)
      .getOrElse(Nil)

    val inputDecoder: Document.Decoder[I] =
      Document.Decoder.fromSchema(endpoint.input)

    smokeTests.map { tc =>
      SmokeTest[F](
        id = tc.id,
        serviceId = serviceId,
        operationId = endpoint.id,
        tags = tc.tags.map(_.map(_.value)).getOrElse(Nil),
        run = runOne(tc, endpoint, inputDecoder, poly)
      )
    }
  }

  private def runOne[F[_], Op[_, _, _, _, _], I, E, O, SI, SO](
      tc: SmokeTestCase,
      endpoint: smithy4s.Endpoint[Op, I, E, O, SI, SO],
      inputDecoder: Document.Decoder[I],
      poly: smithy4s.kinds.PolyFunction5[Op, Kind1[F]#toKind5]
  )(implicit F: MonadThrow[F]): F[SmokeTestResult] = {
    val params = tc.params.getOrElse(Document.obj())

    inputDecoder.decode(params) match {
      case Left(err) =>
        F.pure(
          SmokeTestResult.Fail(
            s"Failed to decode input params: ${err.getMessage}",
            Some(err)
          )
        )
      case Right(input) =>
        poly(endpoint.wrap(input)).attempt.map { result =>
          checkExpectation(tc.expect, result, endpoint.error)
        }
    }
  }

  private def checkExpectation[E, O](
      expect: Expectation,
      result: Either[Throwable, O],
      errorSchema: Option[ErrorSchema[E]]
  ): SmokeTestResult = {
    expect match {
      case Expectation.SuccessCase =>
        result match {
          case Right(_) => SmokeTestResult.Pass
          case Left(err) =>
            SmokeTestResult.Fail(
              s"Expected success but got error: ${err.getMessage}",
              Some(err)
            )
        }

      case Expectation.FailureCase(failure) =>
        result match {
          case Left(err) =>
            failure.errorId match {
              case None => SmokeTestResult.Pass
              case Some(expectedErrorId) =>
                if (matchesErrorId(err, expectedErrorId, errorSchema))
                  SmokeTestResult.Pass
                else
                  SmokeTestResult.Fail(
                    s"Expected error ${expectedErrorId.show} but got: ${err.getClass.getName}: ${err.getMessage}",
                    Some(err)
                  )
            }
          case Right(_) =>
            val expectedDesc = failure.errorId
              .map(_.show)
              .getOrElse("any error")
            SmokeTestResult.Fail(
              s"Expected failure ($expectedDesc) but call succeeded"
            )
        }
    }
  }

  private def matchesErrorId[E](
      err: Throwable,
      expectedErrorId: ShapeId,
      errorSchema: Option[ErrorSchema[E]]
  ): Boolean = {
    errorSchema.exists { es =>
      es.liftError(err).exists { lifted =>
        es.alternatives.exists { alt =>
          alt.schema.shapeId == expectedErrorId &&
          alt.project.lift(lifted).isDefined
        }
      }
    }
  }
}
