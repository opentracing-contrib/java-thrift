/*
 * Copyright 2017-2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.thrift;


import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import custom.CustomService;
import custom.CustomService.AsyncClient;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.thrift.TProcessor;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TracingTest {

  private static final MockTracer mockTracer = spy(new MockTracer(new ThreadLocalScopeManager(),
      MockTracer.Propagator.TEXT_MAP));
  private TServer server;


  @BeforeClass
  public static void init() {
    GlobalTracer.register(mockTracer);
  }

  @Before
  public void before() throws Exception {
    mockTracer.reset();
    reset(mockTracer);
  }

  @After
  public void after() {
    stopServer();
  }

  @Test
  public void newClientOldServer() throws Exception {
    int port = 8884;
    startOldServer(port);

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);

    CustomService.Client client = new CustomService.Client(new SpanProtocol(protocol));
    assertEquals("Say Old Server", client.say("Old", "Server"));

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(1));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(1, mockSpans.size());

    checkSpans(mockSpans, "say", 1);
    assertNull(mockTracer.activeSpan());
    verify(mockTracer, times(1)).buildSpan(anyString());
  }

  @Test
  public void oldClientNewSever() throws Exception {
    int port = 8885;
    startNewServer(port);

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    assertEquals("Say Hello World", client.say("Hello", "World"));

    await().atMost(5, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(1));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(1, mockSpans.size());

    checkSpans(mockSpans, "say", 1);
    assertNull(mockTracer.activeSpan());
    verify(mockTracer, times(1)).buildSpan(anyString());
  }

  @Test
  public void newClientNewServer() throws Exception {
    int port = 8886;
    startNewServer(port);

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);

    CustomService.Client client = new CustomService.Client(new SpanProtocol(protocol));
    assertEquals("Say Good bye World", client.say("Good bye", "World"));

    await().atMost(5, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    checkSpans(mockSpans, "say", 1);
    assertNull(mockTracer.activeSpan());
    verify(mockTracer, times(2)).buildSpan(anyString());
  }

  @Test
  public void withoutArgs() throws Exception {
    int port = 8887;
    startNewServer(port);

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(new SpanProtocol(protocol));

    assertEquals("no args", client.withoutArgs());

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    checkSpans(mockSpans, "withoutArgs", 1);
    assertNull(mockTracer.activeSpan());

    verify(mockTracer, times(2)).buildSpan(anyString());
  }

  @Test
  public void withError() throws Exception {
    int port = 8888;
    startNewServer(port);

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(new SpanProtocol(protocol));

    try {
      assertEquals("Say Good bye", client.withError());
      fail();
    } catch (Exception ignore) {
    }

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    checkSpans(mockSpans, "withError", 1);

    for (MockSpan mockSpan : mockSpans) {
      assertEquals(Boolean.TRUE, mockSpan.tags().get(Tags.ERROR.getKey()));
      assertFalse(mockSpan.logEntries().isEmpty());
    }
    assertNull(mockTracer.activeSpan());

    verify(mockTracer, times(2)).buildSpan(anyString());
  }

  @Test
  public void withCollision() throws Exception {
    int port = 8889;
    startNewServer(port);

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(new SpanProtocol(protocol));

    assertEquals("collision", client.withCollision("collision"));

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    checkSpans(mockSpans, "withCollision", 1);
    verify(mockTracer, times(2)).buildSpan(anyString());
  }


  @Test
  public void async() throws Exception {
    int port = 8890;
    startAsyncServer(port);

    SpanProtocol.Factory protocolFactory = new SpanProtocol.Factory(new TBinaryProtocol.Factory(),
        GlobalTracer.get(), false);

    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient
        .say("Async", "World", new TracingAsyncMethodCallback<>(new AsyncMethodCallback<String>() {
          @Override
          public void onComplete(String response) {
            assertEquals("Say Async World", response);
            assertNotNull(mockTracer.activeSpan());
            counter.incrementAndGet();
          }

          @Override
          public void onError(Exception exception) {
            exception.printStackTrace();
          }
        }, protocolFactory));

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));
    assertEquals(1, counter.get());

    List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(2, spans.size());

    checkSpans(spans, "say", 1);
    assertNull(mockTracer.activeSpan());
    verify(mockTracer, times(2)).buildSpan(anyString());
  }

  @Test
  public void oneWay() throws Exception {
    int port = 8891;
    startNewServer(port);

    TTransport transport = new TSocket("localhost", port);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(new SpanProtocol(protocol));

    client.oneWay();

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    assertTrue(mockSpans.get(0).parentId() != 0 || mockSpans.get(1).parentId() != 0);

    checkSpans(mockSpans, "oneWay", 4);
    verify(mockTracer, times(2)).buildSpan(anyString());
  }

  @Test
  public void oneWayAsync() throws Exception {
    int port = 8892;
    startAsyncServer(port);

    SpanProtocol.Factory protocolFactory = new SpanProtocol.Factory(new TBinaryProtocol.Factory(),
        GlobalTracer.get(), false);

    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    AsyncClient asyncClient = new AsyncClient(protocolFactory, clientManager, transport);
    final AtomicInteger counter = new AtomicInteger();
    asyncClient
        .oneWay(new TracingAsyncMethodCallback<>(new AsyncMethodCallback<Void>() {
          @Override
          public void onComplete(Void response) {
            assertNotNull(mockTracer.activeSpan());
            counter.incrementAndGet();
          }

          @Override
          public void onError(Exception exception) {
            exception.printStackTrace();
          }
        }, protocolFactory));

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));
    assertEquals(1, counter.get());

    List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(2, spans.size());

    checkSpans(spans, "oneWay", 4);
    assertNull(mockTracer.activeSpan());
    verify(mockTracer, times(2)).buildSpan(anyString());
  }

  private void startNewServer(int port) throws Exception {
    CustomHandler customHandler = new CustomHandler();
    final TProcessor customProcessor = new CustomService.Processor<CustomService.Iface>(
        customHandler);

    TServerTransport transport = new TServerSocket(port);
    server = new TSimpleServer(new Args(transport).processor(new SpanProcessor(customProcessor)));

    new Thread(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    }).start();
  }

  private void startAsyncServer(int port) throws Exception {
    CustomHandler customHandler = new CustomHandler();
    final TProcessor customProcessor = new CustomService.Processor<CustomService.Iface>(
        customHandler);
    TNonblockingServerSocket tnbSocketTransport = new TNonblockingServerSocket(port, 30000);
    TNonblockingServer.Args tnbArgs = new TNonblockingServer.Args(tnbSocketTransport);
    tnbArgs.processor(new SpanProcessor(customProcessor));

    server = new TNonblockingServer(tnbArgs);
    new Thread(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    }).start();
  }

  private void startOldServer(int port) throws Exception {
    CustomHandler customHandler = new CustomHandler();
    final TProcessor customProcessor = new CustomService.Processor<CustomService.Iface>(
        customHandler);

    TServerTransport transport = new TServerSocket(port);
    server = new TSimpleServer(new Args(transport).processor(customProcessor));

    new Thread(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    }).start();
  }

  private void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  private Callable<Integer> reportedSpansSize() {
    return new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return mockTracer.finishedSpans().size();
      }
    };
  }

  private void checkSpans(List<MockSpan> mockSpans, String name, int messageType) {
    for (MockSpan mockSpan : mockSpans) {
      Object spanKind = mockSpan.tags().get(Tags.SPAN_KIND.getKey());
      assertTrue(spanKind.equals(Tags.SPAN_KIND_CLIENT) || spanKind.equals(Tags.SPAN_KIND_SERVER));
      assertEquals(SpanDecorator.COMPONENT_NAME, mockSpan.tags().get(Tags.COMPONENT.getKey()));
      assertEquals(name, mockSpan.operationName());
      assertEquals(name, mockSpan.tags().get("message.name"));
      assertEquals((byte) messageType, mockSpan.tags().get("message.type"));
      assertNotNull(mockSpan.tags().get("message.seqid"));
      assertEquals(0, mockSpan.generatedErrors().size());
    }
  }

}
