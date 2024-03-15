[![Apache-2.0 license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# OpenTracing Apache Thrift Instrumentation
OpenTracing instrumentation for Apache Thrift. 

Forked and modified by [Spond](https://spond.com).

pom.xml
```xml
<dependency>
    <groupId>com.spond</groupId>
    <artifactId>opentracing-thrift</artifactId>
    <version>VERSION</version>
</dependency>
```

build.gradle
```
api 'com.spond:opentracing-thrift:{VERSION}'
```

## Publishing changes

This fork has been modified with the ability to publish new versions to a [Code Artifact](https://aws.amazon.com/codeartifact/) Maven repository.

Publishing a new version to code artifact can be done manually with the Maven command:
```
./mvnw deploy
```
This command requires the environment variables `CODEARTIFACT_REPO` and `CODEARTIFACT_AUTH_TOKEN` to be set.
- `CODEARTIFACT_REPO`: The address of the code artifact repository to publish to
- `CODEARTIFACT_AUTH_TOKEN`: A valid code artifact auth token with permissions to publish to the provideded repository.

## Usage

Please don't use `FieldID` equal `3333`. It is a _magic number_ for injected span context.

```java
// Instantiate tracer
Tracer tracer = ...

// Optionally register tracer with GlobalTracer
GlobalTracer.register(tracer);

```
### Synchronous mode

####  Server

```java
// Decorate TProcessor with SpanProcessor e.g.
TProcessor processor = ...
TProcessor spanProcessor = new SpanProcessor(processor, tracer);
TServerTransport transport = ...
TServer server = new TSimpleServer(new Args(transport).processor(spanProcessor));

```

#### Client

```java
// Decorate TProtocol with SpanProtocol e.g.

TTransport transport = ...
TProtocol protocol = new TBinaryProtocol(transport);
TProtocol spanProtocol = new SpanProtocol(protocol, tracer)

```

### Asynchronous mode

#### Server

```java
// Decorate TProcessor with SpanProcessor
TProcessor processor = ...
TProcessor spanProcessor = new SpanProcessor(processor, tracer);

TNonblockingServerSocket tnbSocketTransport = new TNonblockingServerSocket(8890, 30000);
TNonblockingServer.Args tnbArgs = new TNonblockingServer.Args(tnbSocketTransport);
tnbArgs.processor(spanProcessor);
TServer server = new TNonblockingServer(tnbArgs);

```

#### Client

```java

// Decorate TProtocolFactory with SpanProtocol.Factory
TProtocolFactory factory = new TBinaryProtocol.Factory();
SpanProtocol.Factory protocolFactory = new SpanProtocol.Factory(factory, tracer, false);

TNonblockingTransport transport = new TNonblockingSocket("localhost", 8890);
TAsyncClientManager clientManager = new TAsyncClientManager();
AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);

// Decorate AsyncMethodCallback with TracingAsyncMethodCallback:
AsyncMethodCallback<T> callback = ...
TracingAsyncMethodCallback<T> tracingCallback = new TracingAsyncMethodCallback(callback, protocolFactory);

asyncClient.callMethod(..., tracingCallback);

```

### Custom Client Span Tags
To customise the tags added to spans on the client side, create a custom implementation of ClientSpanDecorator.

```java

class MyClientSpanDecorator implements ClientSpanDecorator {
    
    @Override
    public void decorate(Span span, TMessage message) {
        // Add custom tags to the span.
    }
    
    @Override
    public void onError(Throwable throwable, Span span) {
        // Add custom tags for when an error is thrown by the thrift call.
    }
}

```

Then pass this into your SpanProtocol.

```java

TProtocol spanProtocol = new SpanProtocol(protocol, tracer, new MyClientSpanDecorator() );

```

If no custom ClientSpanDecorator is provided, the DefaultClientSpanDecorator is used.
This delegates its methods to the static methods in the SpanDecorator class.
The DefaultClientSpanDecorator can be extended if you want to add to the default behaviour.

## License

[Apache 2.0 License](./LICENSE).

[ci-img]: https://travis-ci.org/opentracing-contrib/java-thrift.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/java-thrift
[cov-img]: https://coveralls.io/repos/github/opentracing-contrib/java-thrift/badge.svg?branch=master
[cov]: https://coveralls.io/github/opentracing-contrib/java-thrift?branch=master
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-thrift.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-thrift
