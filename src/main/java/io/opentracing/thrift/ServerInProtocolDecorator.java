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

class ServerInProtocolDecorator extends TProtocolDecorator {

  private final Tracer tracer;
  private final TMessage message;
  private final SpanBuilder spanBuilder;
  private boolean nextSpan;
  private final List<String> mapElements = new ArrayList<>();

  ServerInProtocolDecorator(TProtocol protocol, TMessage message, Tracer tracer) {
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
      byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(bytes, 0, bytes.length);
      mapElements.add(new String(bytes));
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
    Span activeSpan = tracer.activeSpan();
    if (activeSpan == null) {
      activeSpan = spanBuilder.startActive(true).span();
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
      spanBuilder.asChildOf(parent);
    }
    Span span = spanBuilder.startActive(true).span();
    SpanDecorator.decorate(span, message);
  }
}
