package com.hmall.search.domain.query;

import io.swagger.annotations.ApiModel;
import lombok.Data;

@Data
@ApiModel(description = "商品分页查询条件")
public class ItemFiltersQuery {
    private String key;
    private Boolean isAsc;
    private Integer pageNo;
    private Integer pageSize;
    private String sortBy;
    private String brand;
    private String category;
}
