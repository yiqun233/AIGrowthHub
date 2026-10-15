package io.growthhub.common;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class Errors {
    public record ErrorBody(String code, String message, Map<String,String> fieldErrors, String requestId) {}
    private ResponseEntity<ErrorBody> error(int status, String code, String message, Map<String,String> fields) {
        return ResponseEntity.status(status).body(new ErrorBody(code,message,fields,UUID.randomUUID().toString()));
    }
    @ExceptionHandler(ApiException.class) ResponseEntity<ErrorBody> domain(ApiException e) {
        return error(e.status,e.code,e.getMessage(),Map.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
        var fields = new LinkedHashMap<String,String>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        return error(400,"VALIDATION_ERROR","请检查输入字段",fields);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,ConstraintViolationException.class})
    ResponseEntity<ErrorBody> badInput(Exception e) { return error(400,"INVALID_INPUT","参数或日期格式不正确",Map.of()); }
    @ExceptionHandler(OptimisticLockingFailureException.class) ResponseEntity<ErrorBody> conflict(Exception e) {
        return error(409,"VERSION_CONFLICT","记录已更新，请刷新后重试",Map.of());
    }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ErrorBody> integrity(Exception e) {
        return error(409,"DATA_CONFLICT","数据关联或字段约束冲突",Map.of());
    }
}
