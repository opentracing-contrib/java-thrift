package io.opentracing.thrift;

import custom.CustomService;
import org.apache.thrift.TException;


public class CustomHandler implements CustomService.Iface {

  @Override
  public String say(String text) throws TException {
    return "Say " + text;
  }

  @Override
  public String withError() throws TException {
    throw new RuntimeException("fail");
  }
}
