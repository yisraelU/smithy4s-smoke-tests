package smithy4s.tests

import cats.MonadThrow
import smithy4s.Service
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.kinds.FunctorAlgebra

object DynamicSmokeTestRunner {

  def tests[F[_]](
      wrapper: DynamicSchemaIndex.ServiceWrapper,
      makeImpl: MakeImpl[F]
  )(implicit F: MonadThrow[F]): List[SmokeTest[F]] =
    makeImpl(wrapper.service) match {
      case Some(impl) => SmokeTestRunner.tests(wrapper.service, impl)
      case None       => Nil
    }

  /** A function that, given any dynamically loaded service, produces an implementation or None. */
  trait MakeImpl[F[_]] {
    def apply[Alg[_[_, _, _, _, _]]](
        service: Service[Alg]
    ): Option[FunctorAlgebra[Alg, F]]
  }
}
