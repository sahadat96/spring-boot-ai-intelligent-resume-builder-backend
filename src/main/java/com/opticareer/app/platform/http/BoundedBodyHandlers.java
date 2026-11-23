package com.opticareer.app.platform.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Stops an upstream response while streaming, before an oversized body can be buffered. */
public final class BoundedBodyHandlers {
  private BoundedBodyHandlers() {}

  public static HttpResponse.BodyHandler<String> utf8(int maximumBytes) {
    if (maximumBytes < 1) throw new IllegalArgumentException("Response limit must be positive.");
    return information ->
        new Subscriber(
            maximumBytes, information.headers().firstValueAsLong("Content-Length").orElse(-1));
  }

  /** Includes body completion; HttpRequest.timeout alone stops timing after response headers. */
  public static <T> HttpResponse<T> send(
      HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException, InterruptedException {
    var timeout =
        request
            .timeout()
            .orElseThrow(() -> new IllegalArgumentException("A provider timeout is required."));
    var response = client.sendAsync(request, handler);
    try {
      return response.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException expired) {
      response.cancel(true);
      throw new HttpTimeoutException("Upstream response deadline expired.");
    } catch (InterruptedException interrupted) {
      response.cancel(true);
      throw interrupted;
    } catch (ExecutionException failure) {
      if (failure.getCause() instanceof IOException io) throw io;
      if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
      throw new IOException("Upstream response failed.", failure.getCause());
    }
  }

  public static boolean exceeded(Throwable error) {
    for (int depth = 0; error != null && depth < 32; depth++, error = error.getCause())
      if (error instanceof ResponseTooLargeException) return true;
    return false;
  }

  public static final class ResponseTooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    public ResponseTooLargeException() {
      super("Upstream response exceeded its size limit.");
    }
  }

  private static final class Subscriber implements HttpResponse.BodySubscriber<String> {
    private final int maximumBytes;
    private final long declaredLength;
    private final ByteArrayOutputStream bytes;
    private final CompletableFuture<String> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    Subscriber(int maximumBytes, long declaredLength) {
      this.maximumBytes = maximumBytes;
      this.declaredLength = declaredLength;
      bytes = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
    }

    @Override
    public CompletionStage<String> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription candidate) {
      if (subscription != null) {
        candidate.cancel();
        return;
      }
      subscription = candidate;
      if (declaredLength > maximumBytes) {
        failLimit();
        return;
      }
      candidate.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      if (result.isDone()) return;
      for (ByteBuffer buffer : buffers) {
        if (buffer.remaining() > maximumBytes - bytes.size()) {
          failLimit();
          return;
        }
        byte[] part = new byte[buffer.remaining()];
        buffer.get(part);
        bytes.writeBytes(part);
      }
      subscription.request(1);
    }

    private void failLimit() {
      result.completeExceptionally(new ResponseTooLargeException());
      subscription.cancel();
    }

    @Override
    public void onError(Throwable error) {
      result.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
      if (!result.isDone()) result.complete(bytes.toString(StandardCharsets.UTF_8));
    }
  }
}
