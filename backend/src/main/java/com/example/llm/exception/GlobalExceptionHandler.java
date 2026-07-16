package com.example.llm.exception;

import com.example.llm.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Parameter error: {}", e.getMessage());
        Result<Object> result = Result.error(400, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Object>> handleHttpMessageNotReadableException(org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("Message not readable: {}", e.getMessage());
        Result<Object> result = Result.error(400, "请求参数不能为空或格式不正确");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValidException(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation error: {}", message);
        Result<Object> result = Result.error(400, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception e) {
        log.error("System error: ", e);
        // 生产环境只返回通用错误信息，避免泄露内部堆栈或SQL异常
        Result<Object> result = Result.error(500, "系统繁忙，请稍后再试");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
