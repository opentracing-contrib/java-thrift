package io.opentracing.thrift;

import io.opentracing.ActiveSpan;
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
  public boolean process(TProtocol iprot, TProtocol oprot) throws TException {
    TMessage message = iprot.readMessageBegin();

    if (message.type != TMessageType.CALL && message.type != TMessageType.ONEWAY) {
      throw new TException("This should not have happened!?");
    }

    try {
      return processor.process(new ServerProtocolDecorator(iprot, message, tracer), oprot);
    } catch (Exception e) {
      SpanDecorator.onError(e, tracer.activeSpan());
      throw e;
    } finally {
      ActiveSpan activeSpan = tracer.activeSpan();
      if (activeSpan != null) {
        activeSpan.close();
      }
    }
  }
}
