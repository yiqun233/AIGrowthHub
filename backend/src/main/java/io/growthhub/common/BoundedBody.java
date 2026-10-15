package io.growthhub.common;
import java.io.*;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
/** Cancels the transport as soon as a response exceeds its byte budget. */
public final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
 private final int limit;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
 private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
 public BoundedBody(int limit){if(limit<1)throw new IllegalArgumentException();this.limit=limit;}
 @Override public CompletionStage<byte[]> getBody(){return result;}
 @Override public void onSubscribe(Flow.Subscription s){if(subscription!=null){s.cancel();return;}subscription=s;s.request(1);}
 @Override public void onNext(List<ByteBuffer> buffers){
  if(result.isDone())return;
  for(var buffer:buffers){if((long)bytes.size()+buffer.remaining()>limit){subscription.cancel();result.completeExceptionally(new IOException("Response exceeds byte limit"));return;}byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
  subscription.request(1);
 }
 @Override public void onError(Throwable e){result.completeExceptionally(e);}
 @Override public void onComplete(){result.complete(bytes.toByteArray());}
}
