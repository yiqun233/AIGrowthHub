package io.growthhub.common;
public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public static ApiException missing() { return new ApiException(404,"NOT_FOUND","记录不存在"); }
    public static void version(Long actual, Long expected) {
        if (expected == null) throw new ApiException(400,"VERSION_REQUIRED","请提交记录版本");
        if (!expected.equals(actual)) throw new ApiException(409,"VERSION_CONFLICT","记录已更新，请刷新后重试");
    }
}
