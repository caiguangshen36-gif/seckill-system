package com.mvp.common.dto;

import lombok.Data;

/**
 * 分页请求参数类
 * 用于封装分页查询所需的页码和每页大小参数
 */
@Data
public class PageRequest {
    private Integer pageNum = 1;    // 当前页码，默认值为1
    private Integer pageSize = 10;  // 每页显示的记录数，默认值为10
}