[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# OpenTracing Apache Thrift Instrumentation
OpenTracing instrumentation for Apache Thrift

pom.xml
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-thrift</artifactId>
    <version>0.0.6</version>
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

###  Server

```java
// Decorate TProcessor with SpanProcessor e.g.
TProcessor processor = ...
TProcessor spanProcessor = new SpanProcessor(processor, tracer);
TServerTransport transport = ...
TServer server = new TSimpleServer(new Args(transport).processor(spanProcessor));

```

### Client

```java
// Decorate TProtocol with SpanProtocol e.g.

TTransport transport = ...
TProtocol protocol = new TBinaryProtocol(transport);
TProtocol spanProtocol = new SpanProtocol(protocol, tracer)

```

[ci-img]: https://travis-ci.org/opentracing-contrib/java-thrift.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/java-thrift
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-thrift.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-thrift
