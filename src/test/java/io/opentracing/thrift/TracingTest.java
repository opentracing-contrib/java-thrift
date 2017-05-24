package io.opentracing.thrift;


import static com.jayway.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import custom.CustomService;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TracingTest {

  private static final MockTracer mockTracer = new MockTracer(new ThreadLocalActiveSpanSource(),
      MockTracer.Propagator.TEXT_MAP);
  private TServer server;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static void init() {
    GlobalTracer.register(mockTracer);
  }

  @Before
  public void before() throws Exception {
    mockTracer.reset();
    startServer();
  }

  @After
  public void after() {
    stopServer();
  }

  @Test
  public void test() throws Exception {
    TTransport transport = new TSocket("localhost", 8888);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client = new CustomService.Client(protocol);

    assertEquals("Say Hello", client.say("Hello"));

    CustomService.Client client2 = new CustomService.Client(new SpanProtocol(protocol));
    assertEquals("Say Good bye", client2.say("Good bye"));

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(3));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(3, mockSpans.size());

    checkSpans(mockSpans, "say");
  }

  @Test
  public void withError() throws Exception {
    TTransport transport = new TSocket("localhost", 8888);
    transport.open();

    TProtocol protocol = new TBinaryProtocol(transport);
    CustomService.Client client2 = new CustomService.Client(new SpanProtocol(protocol));

    thrown.expect(TTransportException.class);
    assertEquals("Say Good bye", client2.withError());

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(), equalTo(2));

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(2, mockSpans.size());

    checkSpans(mockSpans, "withError");

    for (MockSpan mockSpan : mockSpans) {
      if (mockSpan.parentId() > 0) {
        assertEquals(Boolean.TRUE, mockSpan.tags().get(Tags.ERROR.getKey()));
        assertFalse(mockSpan.logEntries().isEmpty());
      }

    }
  }

  private void startServer() throws Exception {
    CustomHandler CustomHandler = new CustomHandler();
    final TProcessor CustomProcessor = new CustomService.Processor<CustomService.Iface>(
        CustomHandler);

    TServerTransport transport = new TServerSocket(8888);
    server = new TSimpleServer(new Args(transport).processor(new SpanProcessor(CustomProcessor)));

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

  private void checkSpans(List<MockSpan> mockSpans, String name) {
    for (MockSpan mockSpan : mockSpans) {
      Object spanKind = mockSpan.tags().get(Tags.SPAN_KIND.getKey());
      assertTrue(spanKind.equals(Tags.SPAN_KIND_CLIENT) || spanKind.equals(Tags.SPAN_KIND_SERVER));
      assertEquals(SpanDecorator.COMPONENT_NAME, mockSpan.tags().get(Tags.COMPONENT.getKey()));
      assertEquals(name, mockSpan.operationName());
      assertEquals(name, mockSpan.tags().get("message.name"));
      assertEquals((byte) 1, mockSpan.tags().get("message.type"));
      assertEquals(1, mockSpan.tags().get("message.seqid"));
      assertEquals(0, mockSpan.generatedErrors().size());
      String operationName = mockSpan.operationName();
      assertTrue(operationName.equals("say"));
    }
  }

}
