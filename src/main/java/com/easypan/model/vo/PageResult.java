package com.easypan.model.vo;

import java.util.List;

/**
 * 分页统一返回VO
 * Java Record 不可变数据载体，用于封装分页查询结果返回前端
 * @param pageNum 当前页码
 * @param pageSize 每页条数
 * @param total 数据总条数
 * @param totalPages 总页数
 * @param records 当前页数据列表
 * @param <T> 列表内数据泛型类型
 */
public record PageResult<T>(
        long pageNum,
        long pageSize,
        long total,
        long totalPages,
        List<T> records
) {
}