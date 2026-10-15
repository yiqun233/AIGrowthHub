package io.growthhub.coach;
public class ProviderFailure extends RuntimeException {
    public final String code;public final boolean retryable;public final int retryAfterSeconds;
    public ProviderFailure(String code,String message,boolean retryable,int retryAfterSeconds){super(message);this.code=code;this.retryable=retryable;this.retryAfterSeconds=retryAfterSeconds;}
}
