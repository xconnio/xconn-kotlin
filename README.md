# xconn-kotlin
WAMP v2 Client for kotlin

## Installation

To install `xconn-kotlin`, add the following in your `build.gradle` file:

### Install from maven central

```kotlin
dependencies {
    implementation("io.xconn:xconn:0.1.0-alpha.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
```

### Install from github

```kotlin
dependencies {
    implementation("com.github.xconnio.xconn-kotlin:xconn:340feea4fb")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
```

## Client

Creating a client:

```kotlin
package io.xconn

import io.xconn.xconn.connectAnonymous

suspend fun main() {
    val session = connectAnonymous()
}
```

Once the session is established, you can perform WAMP actions. Below are examples of all 4 WAMP
operations:

### Subscribe to a topic

```kotlin
suspend fun exampleSubscribe(session: Session) {
    session.subscribe("io.xconn.example", { event ->
        print("Received Event: args=${event.args}, kwargs=${event.kwargs}, details=${event.details}")
    }).await()
    print("Subscribed to topic 'io.xconn.example'")
}
```

### Publish to a topic

```kotlin
suspend fun examplePublish(session: Session) {
    session.publish("io.xconn.example", args = listOf("test"), kwargs = mapOf("key" to "value"))?.await()
    print("Published to topic 'io.xconn.example'")
}
```

### Register a procedure

```kotlin
suspend fun exampleRegister(session: Session) {
    session.register("io.xconn.echo", { invocation ->
        Result(args = invocation.args, kwargs = invocation.kwargs)
    }).await()
    print("Registered procedure 'io.xconn.echo'")
}
```

### Call a procedure

```kotlin
suspend fun exampleCall(session: Session) {
    val result = session.call(
        "io.xconn.echo",
        args = listOf(1, 2),
        kwargs = mapOf("key" to "value")
    ).await()
    print("Call result: args=${result.args}, kwargs=${result.kwargs}, details=${result.details}");
}
```

## Authentication

Authentication is straightforward.

### Ticket Auth

```kotlin
val session = connectTicket("ws://localhost:8080/ws", "realm1", "authid", "ticket")
```

### Challenge Response Auth

```kotlin
val session = connectCRA("ws://localhost:8080/ws", "realm1", "authid", "secret")
```

### Cryptosign Auth

```kotlin
val session = connectCryptosign("ws://localhost:8080/ws", "realm1", "authid", "150085398329d255ad69e82bf47ced397bcec5b8fbeecd28a80edbbd85b49081")
```

For more detailed examples or usage, refer to the sample [example](./example/src/main/kotlin) of the project.
