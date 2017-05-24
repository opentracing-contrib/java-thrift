package io.opentracing.thrift;


import com.google.gson.Gson;
import io.opentracing.ActiveSpan;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;

/**
 * <code>SpanProtocol</code> is a protocol-independent concrete decorator that allows a Thrift
 * client to communicate with a tracing Thrift server, by prepending the span context to the
 * function name during function calls.
 *
 * <p>NOTE: THIS IS NOT USED BY SERVERS.  On the server, use {@link SpanProcessor} to handle
 * requests from a tracing client.
 *
 * <p>Inspired by {@link org.apache.thrift.protocol.TMultiplexedProtocol}
 */
public class SpanProtocol extends TProtocolDecorator {

  private final Gson gson = new Gson();

  static final String SEPARATOR = "$span$";
  static final int SEPARATOR_LENGTH = SEPARATOR.length();

  /**
   * Encloses the specified protocol.
   *
   * @param protocol All operations will be forward to this protocol.  Must be non-null.
   */
  public SpanProtocol(TProtocol protocol) {
    super(protocol);
  }

  /**
   * Prepends the span context to the function name, separated by SEPARATOR
   *
   * @param tMessage The original message.
   * @throws TException Passed through from wrapped <code>TProtocol</code> instance.
   */
  @Override
  public void writeMessageBegin(TMessage tMessage) throws TException {
    if (tMessage.type == TMessageType.CALL || tMessage.type == TMessageType.ONEWAY) {
      ActiveSpan span = GlobalTracer.get().buildSpan(tMessage.name)
          .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
          .startActive();

      SpanDecorator.decorate(span, tMessage.name, tMessage);

      Map<String, String> map = new HashMap<>();
      TextMapInjectAdapter adapter = new TextMapInjectAdapter(map);
      GlobalTracer.get().inject(span.context(), Builtin.TEXT_MAP, adapter);

      try {
        super.writeMessageBegin(new TMessage(
            mapToString(map) + SEPARATOR + tMessage.name,
            tMessage.type,
            tMessage.seqid
        ));
      } catch (Exception e) {
        SpanDecorator.onError(e, span);
      } finally {
        span.close();
      }

    } else {
      super.writeMessageBegin(tMessage);
    }
  }

  private String mapToString(Map<String, String> map) throws TException {
    return gson.toJson(map);
  }
}
