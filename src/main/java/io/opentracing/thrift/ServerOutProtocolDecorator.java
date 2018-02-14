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

import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;

class ServerOutProtocolDecorator extends TProtocolDecorator {

  private final Tracer tracer;

  ServerOutProtocolDecorator(TProtocol protocol, Tracer tracer) {
    super(protocol);
    this.tracer = tracer;
  }

  @Override
  public void writeMessageBegin(TMessage tMessage) throws TException {
    if (tracer.activeSpan() != null) {
      tracer.activeSpan().setTag(SpanDecorator.MESSAGE_TYPE, tMessage.type);
      if (tMessage.type == TMessageType.EXCEPTION) {
        tracer.activeSpan().setTag(Tags.ERROR.getKey(), Boolean.TRUE);
      }
    }
    super.writeMessageBegin(tMessage);
  }

}
