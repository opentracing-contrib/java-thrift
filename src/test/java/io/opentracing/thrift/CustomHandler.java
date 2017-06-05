package io.opentracing.thrift;

import custom.CustomService;
import org.apache.thrift.TException;


public class CustomHandler implements CustomService.Iface {

  @Override
  public String say(String text, String text2) throws TException {
    return "Say " + text + " " + text2;
  }

  @Override
  public String withoutArgs() throws TException {
    return "no args";
  }

  @Override
  public String withError() throws TException {
    throw new RuntimeException("fail");
  }

  @Override
  public String withCollision(String input) throws TException {
    return input;
  }
}
