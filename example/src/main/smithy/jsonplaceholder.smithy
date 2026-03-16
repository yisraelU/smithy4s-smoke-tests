$version: "2.0"

namespace example.jsonplaceholder

use smithy.test#smokeTests
use alloy#simpleRestJson

@simpleRestJson
service JsonPlaceholderService {
    operations: [GetPost, GetUser]
}

@smokeTests([
    {
        id: "GetPostSuccess"
        params: { id: 1 }
        expect: { success: {} }
    },
    {
        id: "GetPostNotFound"
        params: { id: 0 }
        expect: {
            failure: {}
        }
    }
])
@http(method: "GET", uri: "/posts/{id}", code: 200)
@readonly
operation GetPost {
    input := {
        @required
        @httpLabel
        id: Integer
    }
    output := {
        @required
        id: Integer
        @required
        userId: Integer
        @required
        title: String
        @required
        body: String
    }
    errors: [NotFoundError]
}

@smokeTests([
    {
        id: "GetUserSuccess"
        params: { id: 1 }
        expect: { success: {} }
    }
])
@http(method: "GET", uri: "/users/{id}", code: 200)
@readonly
operation GetUser {
    input := {
        @required
        @httpLabel
        id: Integer
    }
    output := {
        @required
        id: Integer
        @required
        name: String
        @required
        username: String
        @required
        email: String
    }
    errors: [NotFoundError]
}

@error("client")
@httpError(404)
structure NotFoundError {
    @required
    message: String
}
