package com.snail.platform.control.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.function.Function;

/** 统一分页响应结构。 */
public record PageView<T>(List<T> items, long total, long page, long size) {

    /** 统一收敛页码与页大小，避免超大 size 拖垮数据库。 */
    public static <T> Page<T> paging(int page, int size) {
        return new Page<>(Math.max(1, page), Math.min(100, Math.max(1, size)));
    }

    public static <T> PageView<T> of(Page<T> page) {
        return new PageView<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <E, T> PageView<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageView<>(page.getRecords().stream().map(mapper).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
