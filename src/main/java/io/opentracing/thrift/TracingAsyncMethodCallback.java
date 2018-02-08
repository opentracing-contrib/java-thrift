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

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.apache.thrift.async.AsyncMethodCallback;

public class TracingAsyncMethodCallback<T> implements AsyncMethodCallback<T> {

  private final AsyncMethodCallback<T> callback;
  private final SpanHolder spanHolder;
  private final Tracer tracer;

  public TracingAsyncMethodCallback(AsyncMethodCallback<T> callback, SpanProtocol.Factory factory) {
    this.callback = callback;
    this.spanHolder = factory.getSpanHolder();
    this.tracer = factory.getTracer();
  }

  @Override
  public void onComplete(T response) {
    final Span span = spanHolder.getSpan();
    Scope scope = null;
    if (span != null) {
      scope = tracer.scopeManager().activate(span, true);
    }
    try {
      callback.onComplete(response);
    } finally {
      if (scope != null) {
        scope.close();
        spanHolder.clear();
      }
    }
  }

  @Override
  public void onError(Exception exception) {
    final Span span = spanHolder.getSpan();
    Scope scope = null;
    if (span != null) {
      scope = tracer.scopeManager().activate(span, true);
    }
    try {
      callback.onError(exception);
    } finally {
      if (scope != null) {
        SpanDecorator.onError(exception, span);
        scope.close();
        spanHolder.clear();
      }
    }
  }
}
