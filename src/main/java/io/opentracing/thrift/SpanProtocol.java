package io.opentracing.thrift;


import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransportException;

/**
 * <code>SpanProtocol</code> is a protocol-independent concrete decorator that allows a Thrift
 * client to communicate with a tracing Thrift server, by adding the span context to the
 * string field during function calls.
 *
 * <p>NOTE: THIS IS NOT USED BY SERVERS.  On the server, use {@link SpanProcessor} to handle
 * requests from a tracing client.
 */
public class SpanProtocol extends TProtocolDecorator {

  private final Tracer tracer;
  static final short SPAN_FIELD_ID = 3333; // Magic number

  /**
   * Encloses the specified protocol.
   * Take tracer from GlobalTracer
   *
   * @param protocol All operations will be forward to this protocol.
   */
  public SpanProtocol(TProtocol protocol) {
    this(protocol, GlobalTracer.get());
  }

  /**
   * Encloses the specified protocol.
   *
   * @param protocol All operations will be forward to this protocol.
   * @param tracer Tracer.
   */
  public SpanProtocol(TProtocol protocol, Tracer tracer) {
    super(protocol);
    this.tracer = tracer;
  }

  @Override
  public void writeMessageBegin(TMessage tMessage) throws TException {
    ActiveSpan span = tracer.buildSpan(tMessage.name)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .startActive();

    SpanDecorator.decorate(span, tMessage);
    super.writeMessageBegin(tMessage);
  }

  @Override
  public void writeFieldStop() throws TException {
    ActiveSpan span = tracer.activeSpan();
    if (span != null) {
        Map<String, String> map = new HashMap<>();
        TextMapInjectAdapter adapter = new TextMapInjectAdapter(map);
        tracer.inject(span.context(), Builtin.TEXT_MAP, adapter);

        super.writeFieldBegin(new TField("span", TType.MAP, SPAN_FIELD_ID));
        super.writeMapBegin(new TMap(TType.STRING, TType.STRING, map.size()));
        for (Entry<String, String> entry : map.entrySet()) {
          super.writeString(entry.getKey());
          super.writeString(entry.getValue());
        }
        super.writeMapEnd();
        super.writeFieldEnd();
    }

    super.writeFieldStop();
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    try {
      return super.readMessageBegin();
    } catch (TTransportException tte) {
      ActiveSpan span = tracer.activeSpan();
      if (span != null) {
        SpanDecorator.onError(tte, span);
        span.close();
      }
      throw tte;
    }
  }

  @Override
  public void readMessageEnd() throws TException {
    try {
      super.readMessageEnd();
    } finally {
      ActiveSpan span = tracer.activeSpan();
      if (span != null) {
        span.close();
      }
    }
  }
}
