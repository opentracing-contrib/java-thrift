package io.opentracing.thrift;

import io.opentracing.ActiveSpan;
import io.opentracing.References;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.tag.Tags;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TType;

class ServerProtocolDecorator extends TProtocolDecorator {

  private final Tracer tracer;
  private final TMessage message;
  private final SpanBuilder spanBuilder;
  private boolean nextSpan;
  private final List<String> mapElements = new ArrayList<>();

  ServerProtocolDecorator(TProtocol protocol, TMessage message, Tracer tracer) {
    super(protocol);
    this.tracer = tracer;
    this.message = message;

    spanBuilder = tracer.buildSpan(message.name)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    return message;
  }

  @Override
  public ByteBuffer readBinary() throws TException {
    ByteBuffer byteBuffer = super.readBinary();
    if (nextSpan) {
      mapElements.add(new String(byteBuffer.array()));
    }

    return byteBuffer;
  }

  @Override
  public TField readFieldBegin() throws TException {
    TField tField = super.readFieldBegin();
    if (tField.id == SpanProtocol.SPAN_FIELD_ID && tField.type == TType.MAP) {
      nextSpan = true;
    }
    return tField;
  }

  @Override
  public void readFieldEnd() throws TException {
    if (nextSpan) {
      nextSpan = false;
      buildSpan();
    }
    super.readFieldEnd();
  }

  @Override
  public void readMessageEnd() throws TException {
    ActiveSpan activeSpan = tracer.activeSpan();
    if (activeSpan == null) {
      activeSpan = spanBuilder.startActive();
      SpanDecorator.decorate(activeSpan, message);
    }
    super.readMessageEnd();
  }

  private void buildSpan() {
    if (mapElements.isEmpty()) {
      return;
    }

    Map<String, String> mapSpanContext = new HashMap<>();
    for (int i = 0; i < mapElements.size() - 1; i += 2) {
      mapSpanContext.put(mapElements.get(i), mapElements.get(i + 1));
    }

    SpanContext parent = tracer
        .extract(Builtin.TEXT_MAP, new TextMapExtractAdapter(mapSpanContext));

    if (parent != null) {
      spanBuilder.addReference(References.FOLLOWS_FROM, parent);
    }
    ActiveSpan span = spanBuilder.startActive();
    SpanDecorator.decorate(span, message);
  }
}
