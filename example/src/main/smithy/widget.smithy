$version: "2.0"

namespace example.widget

use smithy.test#smokeTests
use alloy#simpleRestJson

@simpleRestJson
service WidgetService {
    operations: [GetWidget]
}

@smokeTests([
    {
        id: "GetWidgetSuccess"
        params: { id: "foo-123" }
        expect: { success: {} }
    },
    {
        id: "GetWidgetNotFound"
        params: { id: "does-not-exist" }
        expect: {
            failure: { errorId: WidgetNotFoundError }
        }
    }
])
@http(method: "GET", uri: "/widgets/{id}")
@readonly
operation GetWidget {
    input := {
        @required
        @httpLabel
        id: String
    }
    output := {
        @required
        id: String
        @required
        name: String
    }
    errors: [WidgetNotFoundError]
}

@error("client")
@httpError(404)
structure WidgetNotFoundError {
    @required
    message: String
}
