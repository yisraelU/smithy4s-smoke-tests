$version: "2.0"

namespace example.httpbin

use smithy.test#smokeTests
use alloy#simpleRestJson

@simpleRestJson
service HttpBinService {
    operations: [GetStatus]
}

@smokeTests([
    {
        id: "StatusOk"
        params: { code: 200 }
        expect: { success: {} }
    },
    {
        id: "StatusNotFound"
        params: { code: 404 }
        expect: { failure: { errorId: NotFoundError } }
        tags: ["negative"]
    },
    {
        id: "StatusServerError"
        params: { code: 500 }
        expect: { failure: { errorId: InternalServerError } }
        tags: ["negative"]
    },
    {
        id: "StatusForbidden"
        params: { code: 403 }
        expect: { failure: { errorId: ForbiddenError } }
        tags: ["negative"]
    }
])
@http(method: "GET", uri: "/status/{code}", code: 200)
@readonly
operation GetStatus {
    input := {
        @required
        @httpLabel
        code: Integer
    }
    errors: [NotFoundError, InternalServerError, ForbiddenError]
}

@error("client")
@httpError(404)
structure NotFoundError {}

@error("server")
@httpError(500)
structure InternalServerError {}

@error("client")
@httpError(403)
structure ForbiddenError {}
