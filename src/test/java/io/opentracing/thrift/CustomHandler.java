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

import custom.Address;
import custom.CustomService;
import custom.User;
import custom.UserWithAddress;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TException;


public class CustomHandler implements CustomService.Iface {

  @Override
  public String say(String text, String text2) throws TException {
    return "Say " + text + " " + text2;
  }

  @Override
  public String withDelay(int seconds) throws TException {
    try {
      TimeUnit.SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return "delay " + seconds;
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

  @Override
  public void oneWay() throws TException {

  }

  @Override
  public void oneWayWithError() throws TException {
    throw new RuntimeException("fail");
  }

  @Override
  public UserWithAddress save(User user, Address address) throws TException {
    return new UserWithAddress(user, address);
  }
}
