package io.opentracing.thrift;


import io.opentracing.ActiveSpan;
import io.opentracing.tag.Tags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.protocol.TMessage;

class SpanDecorator {

  static final String COMPONENT_NAME = "java-thrift";

  static void decorate(ActiveSpan span, String name, TMessage message) {
    span.setTag(Tags.COMPONENT.getKey(), COMPONENT_NAME);
    span.setTag("message.name", name);
    span.setTag("message.type", message.type);
    span.setTag("message.seqid", message.seqid);
  }

  static void onError(Throwable throwable, ActiveSpan span) {
    span.setTag(Tags.ERROR.getKey(), Boolean.TRUE);
    span.log(errorLogs(throwable));
  }

  private static Map<String, Object> errorLogs(Throwable throwable) {
    Map<String, Object> errorLogs = new HashMap<>(5);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.kind", throwable.getClass().getName());
    errorLogs.put("error.object", throwable);

    errorLogs.put("message", throwable.getMessage());

    StringWriter sw = new StringWriter();
    throwable.printStackTrace(new PrintWriter(sw));
    errorLogs.put("stack", sw.toString());

    return errorLogs;
  }
}
