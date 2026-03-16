# smithy4s-smoke-tests

A library and CLI for running [Smithy smoke tests](https://smithy.io/2.0/additional-specs/smoke-tests.html) with [smithy4s](https://disneystreaming.github.io/smithy4s/). 
Inspired heavily by the Compliance Tests module of Smithy4s

Extracts `@smokeTests` annotations from Smithy models and turns them into executable test cases. Supports both codegen'd services and dynamically loaded models (no codegen required).

## Modules

| Module | Description |
|--------|-------------|
| **core** | Runtime library — `SmokeTestRunner`, `SmokeTest[F]`, `SmokeTestResult` |
| **dynamic** | Dynamic model support — `DynamicSmokeTestRunner` (no codegen required) |
| **cli** | Command-line tool for running smoke tests from Smithy files |
| **example** | Demo specs using weaver-test |

## Library Usage

### With codegen'd services

```scala
import smithy4s.tests.{SmokeTestRunner, SmokeTestResult}
import cats.effect.IO

// Given a smithy4s service and implementation:
val tests = SmokeTestRunner.tests(MyService, myImpl)

tests.foreach { test =>
  println(s"${test.id} - ${test.serviceId}#${test.operationId}")
  // test.run: IO[SmokeTestResult]
}
```

### With dynamically loaded models

```scala
import cats.effect.IO
import smithy4s.{Service, ShapeId}
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.kinds.FunctorAlgebra
import smithy4s.tests.DynamicSmokeTestRunner
import software.amazon.smithy.model.Model

// Load and assemble a Smithy model at runtime
val model = Model.assembler()
  .addImport("my-service.smithy")
  .discoverModels()
  .assemble()
  .unwrap()

val index = DynamicSchemaIndex.loadModel(model)
val wrapper = index.getService(ShapeId("com.example", "MyService")).get

// Build an HTTP client for each dynamically loaded service
val makeImpl = new DynamicSmokeTestRunner.MakeImpl[IO] {
  def apply[Alg[_[_, _, _, _, _]]](
      service: Service[Alg]
  ): Option[FunctorAlgebra[Alg, IO]] =
    SimpleRestJsonBuilder(service)
      .client(httpClient)  // an org.http4s.client.Client[IO]
      .uri(baseUri)        // an org.http4s.Uri
      .make
      .toOption
}

val tests = DynamicSmokeTestRunner.tests(wrapper, makeImpl)
```

### Integration with test frameworks

The `SmokeTest[F]` type is framework-agnostic. Here's an example with weaver:

```scala
import weaver.SimpleIOSuite
import smithy4s.tests.{SmokeTestRunner, SmokeTestResult}

object MySmokeSpec extends SimpleIOSuite {
  val tests = SmokeTestRunner.tests(MyService, myImpl)

  tests.foreach { smokeTest =>
    test(smokeTest.id) {
      smokeTest.run.map {
        case SmokeTestResult.Pass       => expect(true)
        case SmokeTestResult.Fail(m, _) => failure(m)
      }
    }
  }
}
```

## CLI Usage

The CLI loads a Smithy model at runtime (no codegen) and discovers or executes `@smokeTests`.

### `list` — Discover tests

List all smoke tests found in a model without executing them:

```bash
sbt "cli/run list --file service.smithy"
cat service.smithy | sbt "cli/run list"
```

Example output:

```
Found 2 smoke test(s):

  WidgetService#GetWidget - GetWidgetSuccess (expect: success)
  WidgetService#GetWidget - GetWidgetNotFound (expect: failure(example.widget#WidgetNotFoundError)) [negative]
```

### `run` — Execute tests

Run smoke tests against a live HTTP service:

```bash
sbt "cli/run run --file service.smithy --url https://api.example.com"
cat service.smithy | sbt "cli/run run --url https://api.example.com"
```

Example output:

```
Running 2 smoke test(s)...

  [PASS] WidgetService#GetWidget - GetWidgetSuccess
  [FAIL] WidgetService#GetWidget - GetWidgetNotFound [negative]
    Expected error example.widget#WidgetNotFoundError but got: ...

Results: 1 passed, 1 failed, 2 total
```

The `run` command creates HTTP clients via `SimpleRestJsonBuilder` and exits with a non-zero code if any tests fail.

### Shared options

Both subcommands accept:

```
--file <path>       Path to a Smithy model file
--jar <path>        Path to a JAR containing Smithy models (repeatable)
--service <name>    Filter to a specific service by name
--tag <tag>         Filter tests by tag
```

If neither `--file` nor `--jar` is provided, the model is read from stdin.

### Example Smithy model

```smithy
$version: "2.0"
namespace com.example

use smithy.test#smokeTests
use alloy#simpleRestJson

@simpleRestJson
service WidgetService {
    operations: [GetWidget]
}

@smokeTests([
    {
        id: "GetWidgetSuccess"
        params: { id: "widget-1" }
        expect: { success: {} }
    },
    {
        id: "GetWidgetNotFound"
        params: { id: "does-not-exist" }
        expect: { failure: { errorId: WidgetNotFoundError } }
    }
])
@http(method: "GET", uri: "/widgets/{id}")
@readonly
operation GetWidget {
    input := { @required @httpLabel id: String }
    output := { @required id: String, @required name: String }
    errors: [WidgetNotFoundError]
}

@error("client")
@httpError(404)
structure WidgetNotFoundError { @required message: String }
```

## Building

```bash
sbt compile          # compile all modules
sbt test             # run tests
sbt "cli/run list"   # list tests (reads from stdin)
sbt "cli/run run"    # run tests (reads from stdin)
```

## Dependencies

- [smithy4s](https://github.com/disneystreaming/smithy4s) — version derived from the smithy4s sbt plugin
- [smithy-smoke-test-traits](https://smithy.io/2.0/additional-specs/smoke-tests.html) — version derived from the smithy4s sbt plugin
- Scala 2.13
