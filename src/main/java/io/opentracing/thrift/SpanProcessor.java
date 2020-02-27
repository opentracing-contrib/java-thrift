/*
 * Copyright 2017-2020 The OpenTracing Authors
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

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;

/**
 * Tracing decorator for {@link TProcessor}
 */
public class SpanProcessor implements TProcessor {

  private final TProcessor processor;
  private final Tracer tracer;

  /**
   * Construct SpanProcessor using tracer from GlobalTracer
   *
   * @param processor processor
   */
  public SpanProcessor(TProcessor processor) {
    this(processor, GlobalTracer.get());
  }

  public SpanProcessor(TProcessor processor, Tracer tracer) {
    this.processor = processor;
    this.tracer = tracer;
  }

  @Override
  public void process(TProtocol iprot, TProtocol oprot) throws TException {
    TMessage message = iprot.readMessageBegin();

    if (message.type != TMessageType.CALL && message.type != TMessageType.ONEWAY) {
      throw new TException("This should not have happened!?");
    }

    final ServerInProtocolDecorator serverInProtocolDecorator = new ServerInProtocolDecorator(iprot,
        message, tracer);
    try {
      processor.process(serverInProtocolDecorator,
          new ServerOutProtocolDecorator(oprot, tracer));
    } catch (Exception e) {
      SpanDecorator.onError(e, tracer.activeSpan());
      throw e;
    } finally {
      serverInProtocolDecorator.closeSpan();
    }
  }
}
