package io.opentracing.thrift;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;

/**
 * In order to work with any protocol, we needed
 * to allow them to call readMessageBegin() and get a TMessage in exactly
 * the standard format, without the span context prepended to TMessage.name.
 */
class StoredMessageProtocol extends TProtocolDecorator {

  private final TMessage messageBegin;

  StoredMessageProtocol(TProtocol protocol, TMessage messageBegin) {
    super(protocol);
    this.messageBegin = messageBegin;
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    return messageBegin;
  }
}
