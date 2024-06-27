package com.hmall.search.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Api(tags = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(
            HttpHost.create("http://192.168.139.10:9200")
    ));

    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDoc> search(ItemPageQuery itemPageQuery) throws IOException {
        // 创建BoolQueryBuilder
        BoolQueryBuilder boolQuery = new BoolQueryBuilder();

        // 添加过滤条件
        boolQuery.filter(new TermQueryBuilder("brand", itemPageQuery.getBrand()));
        boolQuery.filter(new TermQueryBuilder("category", itemPageQuery.getCategory()));
        boolQuery.filter(new RangeQueryBuilder("price")
                .gte(itemPageQuery.getMinPrice())
                .lte(itemPageQuery.getMaxPrice()));

        // 添加必须匹配的条件
        boolQuery.must(new MatchQueryBuilder("name", itemPageQuery.getKey()));

        // 构建搜索源构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(boolQuery)
                // 添加排序
                .sort(new FieldSortBuilder("sold").order(SortOrder.DESC))
                // 设置每页大小和起始位置
                .size(itemPageQuery.getPageSize())
                .from(itemPageQuery.getPageNo());

        // 创建搜索请求对象
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("items"); // 指定索引名
        searchRequest.source(sourceBuilder);

        // 执行搜索请求
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

        SearchHits searchHits = response.getHits();
        // 1.获取总条数
        long total = searchHits.getTotalHits().value;
        List<ItemDoc> itemDocList = new ArrayList<>();
        // 2.遍历结果数组
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            // 3.得到_source，也就是原始json文档
            String source = hit.getSourceAsString();
            // 4.反序列化并打印
            ItemDoc item = JSONUtil.toBean(source, ItemDoc.class);
            itemDocList.add(item);
        }
        Page<ItemDoc> itemDocPage = new Page<ItemDoc>().setRecords(itemDocList);
        itemDocPage.setPages(itemPageQuery.getPageNo());
        itemDocPage.setTotal(total);
        return PageDTO.of(itemDocPage, ItemDoc.class);
    }

}
