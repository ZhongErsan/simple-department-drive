package com.easypan.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 全局统一接口返回封装类
 * 项目所有 Controller 请求统一返回该实体，标准化前后端交互 JSON 格式
 * 实现 Serializable 序列化接口：支持对象网络传输、Redis 缓存、分布式服务序列化传递
 * 泛型<T>：动态适配任意类型业务返回数据（用户实体、文件VO、分页对象、集合等）
 */
@Data
public class Result<T> implements Serializable {

    /**
     * 业务状态码
     * 200：接口请求处理成功
     * 4xx：客户端错误（参数非法、未登录、权限不足、资源不存在）
     * 5xx：服务端内部异常
     * 可复用标准HTTP状态数字，也可自定义项目专属业务错误编码
     */
    private Integer code;

    /**
     * 前端展示提示文本
     * 成功场景：操作成功、查询完成
     * 失败场景：文件名不能为空、登录已过期、文件不存在
     * 前端直接读取该字段用于弹窗、页面文字提示
     */
    private String message;

    /**
     * 接口业务返回数据
     * 查询类接口存放业务实体、列表、分页数据；增删改操作无返回值时为null
     */
    private T data;

    /**
     * 私有无参构造器
     * 禁止外部直接new创建实例，强制使用静态工具方法success()/fail()构建返回对象
     * 统一创建逻辑，避免漏传code、message关键字段，保证返回格式一致性
     */
    private Result() {
    }

    /**
     * 私有全参构造器
     * 仅本类内部静态方法调用，封装对象赋值逻辑，对外屏蔽底层实例化细节
     * @param code 业务状态码
     * @param message 响应提示信息
     * @param data 业务返回数据
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ====================== 成功响应静态工具方法 ======================

    /**
     * 请求成功，无业务返回数据
     * 适用场景：新增文件、删除文件、修改密码等无需返回数据的操作
     * @return 成功返回体，data字段为null
     */
    public static <T> Result<T> success() {
        return new Result<>(
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(),
                null
        );
    }

    /**
     * 请求成功，携带业务数据，使用默认成功提示
     * 适用场景：查询用户信息、查询文件列表、获取文件详情等查询接口
     * @param data 需要返回给前端的业务数据
     * @return 携带业务数据的成功返回体
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(),
                data
        );
    }

    /**
     * 请求成功，自定义成功提示文案 + 携带业务数据
     * 适用场景：需要个性化成功提示，如「文件上传成功」「分享链接创建完成」
     * @param message 自定义成功提示文字
     * @param data 需要返回给前端的业务数据
     * @return 自定义提示+业务数据的成功返回体
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(
                ResultCode.SUCCESS.getCode(),
                message,
                data
        );
    }
    public static <T> Result<T> success(String message){
        return new Result<>(
                ResultCode.SUCCESS.getCode(),
                message,
                null
        );
    }
    // ====================== 失败响应静态工具方法 ======================

    /**
     * 请求失败，传入全局统一错误枚举ResultCode
     * 统一管理项目标准错误（未登录、权限不足、服务器异常等），便于统一维护错误文案与编码
     * @param resultCode 预定义全局错误枚举，内置code与message
     * @return 标准错误返回体，data字段为null
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(
                resultCode.getCode(),
                resultCode.getMessage(),
                null
        );
    }

    /**
     * 请求失败，自定义业务错误码与错误提示文案
     * 适用场景：临时特殊业务报错，无预设ResultCode枚举，灵活自定义错误信息
     * @param code 自定义业务错误码（可复用401/403/404等标准HTTP数字码）
     * @param message 自定义错误提示文字
     * @return 自定义错误返回体，data字段为null
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}