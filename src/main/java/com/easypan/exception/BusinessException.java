package com.easypan.exception;

import com.easypan.common.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常类
 * 用于区分系统原生异常与业务逻辑主动抛出的错误
 * 所有业务校验失败、权限不足、资源不存在、登录失效等场景统一抛出该异常
 * 继承RuntimeException：运行时异常，无需方法显式throws声明，简化代码书写
 * @Getter 自动生成code属性getter方法，全局异常处理器可获取错误码做HTTP状态映射
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 自定义业务错误码，与ResultCode统一规范，同时可兼容标准HTTP状态码(401/403/404/500)
     */
    private final Integer code;

    /**
     * 构造方法1：仅传入错误提示信息
     * 默认使用全局参数错误码 BAD_REQUEST(400)
     * @param message 自定义错误描述文案
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BAD_REQUEST.getCode();
    }

    /**
     * 构造方法2：传入统一枚举错误码对象ResultCode
     * 自动从枚举中读取预设code与message，统一管理错误文案，便于维护
     * @param resultCode 预定义全局错误枚举（如UNAUTHORIZED、FORBIDDEN、SERVER_ERROR）
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 构造方法3：自定义错误码 + 自定义提示文案
     * 适用于特殊业务场景，不使用预设ResultCode，自由指定业务code与消息
     * @param code 自定义业务状态码（可传标准HTTP码401、403，或项目自定义5xx业务码）
     * @param message 自定义错误提示文字
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
