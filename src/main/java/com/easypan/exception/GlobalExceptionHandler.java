package com.easypan.exception;

import com.easypan.common.Result;
import com.easypan.common.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局统一异常处理器
 * 作用：拦截项目中所有@RestController接口抛出的各类异常，统一封装标准JSON返回体
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody，天然返回JSON，适配前后端分离文件网盘项目
 * 分层处理：参数异常、文件上传异常、自定义业务异常、全局未知系统异常四大类
 * 特色：区分HTTP外层状态码与业务自定义编码，前端可通过axios拦截4xx/5xx状态快速捕获错误
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理普通URL/表单必填参数缺失异常
     * 触发场景：接口声明@RequestParam必填参数，前端请求未传递该参数
     * HTTP响应码固定400 Bad Request
     * @param e 缺失普通参数异常对象
     * @return 统一失败响应，提示缺失的参数名称
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingRequestParameter(
            MissingServletRequestParameterException e
    ) {
        return Result.fail(
                ResultCode.BAD_REQUEST.getCode(),
                "缺少请求参数：" + e.getParameterName()
        );
    }

    /**
     * 处理multipart/form-data文件上传请求缺少文件字段异常
     * 触发场景：前端上传接口采用文件上传格式，但未传递后端指定name的文件
     * e.getRequestPartName() 获取后端要求的文件字段名（如file）
     * HTTP响应码固定400
     * @param e 文件部分缺失异常
     * @return 提示缺失的文件参数名
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<?> handleMissingRequestPart(
            MissingServletRequestPartException e
    ) {
        return Result.fail(
                ResultCode.BAD_REQUEST.getCode(),
                "缺少请求参数：" + e.getRequestPartName()
        );
    }

    /**
     * 处理上传文件超出配置最大限制大小异常
     * 对应配置 spring.servlet.multipart.max-file-size / max-request-size
     * HTTP标准状态码413 PAYLOAD_TOO_LARGE 请求体过大
     * @param e 文件超限异常
     * @return 上传超限提示
     */
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return Result.fail(HttpStatus.PAYLOAD_TOO_LARGE.value(), "上传文件或请求体超过大小限制");
    }

    /**
     * 文件上传通用兜底异常
     * 触发场景：上传格式错误、文件流传输中断、非multipart请求调用上传接口
     * HTTP 400
     * @param e 上传通用异常
     * @return 上传格式错误提示
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MultipartException.class)
    public Result<?> handleMultipartException(MultipartException e) {
        return Result.fail(HttpStatus.BAD_REQUEST.value(), "上传请求格式不正确或传输已中断");
    }

    /**
     * 处理@RequestBody JSON实体参数校验异常
     * 触发场景：前端传递JSON请求体，实体类@NotBlank/@Size等校验规则不满足，如某些参数为空
     * 通过流式获取第一个校验失败的自定义message返回前端
     * @param e JSON参数校验异常
     * @return 参数错误提示，优先返回字段自定义提示
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 获取第一个校验失败字段的提示信息，无则返回通用文案
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数错误");

        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理form表单、@RequestParam实体绑定校验异常
     * 区分于MethodArgumentNotValidException：该类用于表单、URL参数绑定校验失败，如某些参数有数量限制
     * @param e 表单参数绑定异常
     * @return 字段校验提示
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数错误");

        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理单个@RequestParam参数校验异常
     * 场景：接口单个参数上加@Min、@NotBlank等校验注解，传参不符合规则
     * @param e 单参数校验异常
     * @return 校验错误信息
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * 统一处理自定义业务异常 BusinessException
     * 特殊点：使用 ResponseEntity 动态修改HTTP外层状态码，而非固定@ResponseStatus
     * 1. 通过resolveBusinessHttpStatus方法将业务code映射为标准HTTP状态码
     * 2. 返回体内部保留自定义业务code，同时外层HTTP状态码符合标准4xx/5xx规范
     * @param e 自定义业务异常
     * @return 携带对应HTTP状态码的标准Result响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(
            BusinessException e
    ) {
        // 根据业务码匹配对应的HTTP状态码
        HttpStatus status = resolveBusinessHttpStatus(
                e.getCode()
        );

        return ResponseEntity
                .status(status)
                .body(
                        Result.fail(
                                e.getCode(),
                                e.getMessage()
                        )
                );
    }

    /**
     * 业务码转HTTP标准状态码映射工具方法
     * 设计目的：统一HTTP协议语义，前端/网关可直接拦截HTTP状态码做统一处理
     * 例如：业务code=401 → HTTP 401未登录；code=403 → HTTP403权限不足
     * 避免所有错误都返回HTTP200，前端只能解析JSON内部code判断错误
     * 转换规则：
     * 1. 若业务码是合法HTTP状态码（401/403/404等）直接使用
     * 2. 业务码>=500 统一映射为服务端500异常
     * 3. 其余未知业务码默认返回400参数错误
     * @param businessCode 自定义业务错误码
     * @return 对应标准HttpStatus枚举
     */
    private HttpStatus resolveBusinessHttpStatus(
            Integer businessCode
    ) {
        if (businessCode != null) {
            // 根据数字解析对应的HTTP状态枚举
            HttpStatus resolved = HttpStatus.resolve(
                    businessCode
            );

            // 如果业务码刚好匹配标准HTTP码（401、403、404等）直接返回
            if (resolved != null) {
                return resolved;
            }

            // 业务码5xx区间，统一标记为服务器内部异常
            if (businessCode >= 500) {
                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }

        // 默认兜底400
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 全局兜底异常处理器
     * 捕获所有上方未单独处理的未知异常：空指针、数据库异常、IO异常等系统错误
     * HTTP固定返回500服务器异常，打印完整异常堆栈用于后端排查
     * @param e 通用顶级异常对象
     * @return 服务器异常统一提示
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 打印异常堆栈日志，线上排查问题使用，生产环境建议替换log.error
        log.error("未处理的未知异常",e);
        return Result.fail(ResultCode.SERVER_ERROR.getCode(), "服务器异常，请稍后再试");
    }
}