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


import io.opentracing.Span;
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
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;
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
  private final SpanHolder spanHolder;
  private final boolean finishSpan;
  static final short SPAN_FIELD_ID = 3333; // Magic number
  private boolean oneWay;
  private boolean injected;

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
    this.spanHolder = new SpanHolder();
    this.finishSpan = true;
  }

  SpanProtocol(TProtocol protocol, Tracer tracer, SpanHolder spanHolder, boolean finishSpan) {
    super(protocol);
    this.tracer = tracer;
    this.spanHolder = spanHolder;
    this.finishSpan = finishSpan;
  }

  @Override
  public void writeMessageBegin(TMessage tMessage) throws TException {
    Span span = tracer.buildSpan(tMessage.name)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start();
    spanHolder.setSpan(span);

    oneWay = tMessage.type == TMessageType.ONEWAY;
    injected = false;

    SpanDecorator.decorate(span, tMessage);
    super.writeMessageBegin(tMessage);
  }

  @Override
  public void writeMessageEnd() throws TException {
    try {
      super.writeMessageEnd();
    } finally {
      Span span = spanHolder.getSpan();
      if (span != null && oneWay && finishSpan) {
        span.finish();
        spanHolder.setSpan(null);
      }
    }
  }

  @Override
  public void writeFieldStop() throws TException {
    if (!injected) {
      Span span = spanHolder.getSpan();
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
        injected = true;
      }
    }

    super.writeFieldStop();
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    try {
      return super.readMessageBegin();
    } catch (TTransportException tte) {
      Span span = spanHolder.getSpan();
      if (span != null) {
        SpanDecorator.onError(tte, span);
        if (finishSpan) {
          span.finish();
          spanHolder.setSpan(null);
        }
      }
      throw tte;
    }
  }

  @Override
  public void readMessageEnd() throws TException {
    try {
      super.readMessageEnd();
    } finally {
      Span span = spanHolder.getSpan();
      if (span != null && finishSpan) {
        span.finish();
        spanHolder.setSpan(null);
      }
    }
  }

  /**
   * Factory
   */
  public static class Factory implements TProtocolFactory {

    private final TProtocolFactory delegate;
    private final SpanHolder spanHolder = new SpanHolder();
    private final Tracer tracer;
    private final boolean finishSpan;

    /**
     * @param delegate actual TProtocolFactory
     * @param tracer tracer
     * @param finishSpan <code>false</code> if {@link TracingAsyncMethodCallback} is used otherwise <code>true</code>
     */
    public Factory(TProtocolFactory delegate, Tracer tracer, boolean finishSpan) {
      this.delegate = delegate;
      this.tracer = tracer;
      this.finishSpan = finishSpan;
    }

    @Override
    public TProtocol getProtocol(TTransport trans) {
      return new SpanProtocol(delegate.getProtocol(trans), tracer, spanHolder, finishSpan);
    }

    SpanHolder getSpanHolder() {
      return spanHolder;
    }

    Tracer getTracer() {
      return tracer;
    }
  }
}
