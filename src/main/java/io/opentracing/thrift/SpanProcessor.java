package io.opentracing.thrift;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.opentracing.ActiveSpan;
import io.opentracing.References;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;

/**
 * Tracing decorator for {@link TProcessor}
 * Inspired by {@link org.apache.thrift.TMultiplexedProcessor}
 */
public class SpanProcessor implements TProcessor {

  private final Gson gson = new Gson();
  private final TProcessor processor;

  public SpanProcessor(TProcessor processor) {
    this.processor = processor;
  }

  @Override
  public boolean process(TProtocol iprot, TProtocol oprot) throws TException {
    TMessage message = iprot.readMessageBegin();

    if (message.type != TMessageType.CALL && message.type != TMessageType.ONEWAY) {
      throw new TException("This should not have happened!?");
    }

    // Extract the span context
    int index = message.name.indexOf(SpanProtocol.SEPARATOR);
    if (index < 0) {
      return noSpanContext(message, iprot, oprot);
    }

    String stringSpanContext = message.name.substring(0, index);

    Map<String, String> mapSpanContext;
    try {
      mapSpanContext = stringToMap(stringSpanContext);
    } catch (JsonSyntaxException e) {
      // looks like there is no span context in message name, collision with SEPARATOR
      return noSpanContext(message, iprot, oprot);
    }
    SpanContext parent = GlobalTracer.get()
        .extract(Builtin.TEXT_MAP, new TextMapExtractAdapter(mapSpanContext));

    String msgName = message.name
        .substring(stringSpanContext.length() + SpanProtocol.SEPARATOR_LENGTH);

    ActiveSpan span = buildSpan(message, msgName, parent);

    // Create a new TMessage, removing the span context
    TMessage standardMessage = new TMessage(
        msgName,
        message.type,
        message.seqid
    );

    return process(span, iprot, oprot, standardMessage);
  }

  private boolean process(ActiveSpan span, TProtocol iprot, TProtocol oprot, TMessage message)
      throws TException {
    // Dispatch processing to the stored processor
    try {
      return processor.process(new StoredMessageProtocol(iprot, message), oprot);
    } catch (Exception e) {
      SpanDecorator.onError(e, span);
      throw e;
    } finally {
      span.close();
    }
  }

  private Map<String, String> stringToMap(String string) {
    return gson.fromJson(string, Map.class);
  }

  private boolean noSpanContext(TMessage message, TProtocol iprot, TProtocol oprot)
      throws TException {
    ActiveSpan span = buildSpan(message, message.name, null);
    return process(span, iprot, oprot, message);
  }

  private ActiveSpan buildSpan(TMessage message, String name, SpanContext parent) {
    SpanBuilder spanBuilder = GlobalTracer.get().buildSpan(name)
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);

    if (parent != null) {
      spanBuilder.addReference(References.FOLLOWS_FROM, parent);
    }
    ActiveSpan span = spanBuilder.startActive();
    SpanDecorator.decorate(span, name, message);
    return span;
  }

}
