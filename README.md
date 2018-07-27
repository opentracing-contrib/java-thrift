[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven]

# OpenTracing Apache Thrift Instrumentation
OpenTracing instrumentation for Apache Thrift

pom.xml
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-thrift</artifactId>
    <version>VERSION</version>
</dependency>
```

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

## License

[Apache 2.0 License](./LICENSE).

[ci-img]: https://travis-ci.org/opentracing-contrib/java-thrift.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/java-thrift
[cov-img]: https://coveralls.io/repos/github/opentracing-contrib/java-thrift/badge.svg?branch=master
[cov]: https://coveralls.io/github/opentracing-contrib/java-thrift?branch=master
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-thrift.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-thrift
