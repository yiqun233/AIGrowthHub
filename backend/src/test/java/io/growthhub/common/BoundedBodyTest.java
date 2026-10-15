package io.growthhub.common;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
class BoundedBodyTest {
 static class Subscription implements Flow.Subscription {boolean cancelled;long requested;public void request(long n){requested+=n;}public void cancel(){cancelled=true;}}
 @Test void collectsOnlyWithinTheLimit(){var body=new BoundedBody(4);var s=new Subscription();body.onSubscribe(s);body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}),ByteBuffer.wrap(new byte[]{3,4})));body.onComplete();assertArrayEquals(new byte[]{1,2,3,4},body.getBody().toCompletableFuture().join());assertFalse(s.cancelled);}
 @Test void oversizedResponseCancelsBeforeBuffering(){var body=new BoundedBody(3);var s=new Subscription();body.onSubscribe(s);body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}),ByteBuffer.wrap(new byte[]{3,4})));assertTrue(s.cancelled);assertThrows(CompletionException.class,()->body.getBody().toCompletableFuture().join());}
 @Test void propagatesTransportFailure(){var body=new BoundedBody(8);body.onError(new IllegalStateException());assertThrows(CompletionException.class,()->body.getBody().toCompletableFuture().join());}
}
