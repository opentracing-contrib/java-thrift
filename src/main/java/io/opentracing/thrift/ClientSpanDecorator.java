/*
 * Copyright 2017-2019 The OpenTracing Authors
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

import io.opentracing.Span;
import org.apache.thrift.protocol.TMessage;

/**
 * Interface for decorating Spans generated on the client side of a Thrift call.
 */
public interface ClientSpanDecorator {
  /**
   * Decorate a span with information from a TMessage.
   *
   * @param span Span.
   * @param message TMessage.
   */
  void decorate(Span span, TMessage message);

  /**
   * Decorate a span with information from a caught throwable.
   *
   * @param span Span.
   * @param throwable Throwable.
   */
  void onError(Throwable throwable, Span span);
}
