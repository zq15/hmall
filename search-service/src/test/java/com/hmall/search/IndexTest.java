package com.hmall.search;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IndexTest {

    private RestHighLevelClient client;

    @BeforeEach
    void setUp() {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.139.10:9200")
        ));
    }

    @Test
    void testConnect() {
        System.out.println(client);
    }

    @Test
    void testMatchAll() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        request.source().query(QueryBuilders.matchAllQuery());
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    private void handleResponse(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        // 1.获取总条数
        long total = searchHits.getTotalHits().value;
        System.out.println("共搜索到" + total + "条数据");
        // 2.遍历结果数组
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            // 3.得到_source，也就是原始json文档
            String source = hit.getSourceAsString();
            // 4.反序列化并打印
            ItemDoc item = JSONUtil.toBean(source, ItemDoc.class);
            System.out.println(item);
        }
    }

    @Test
    void testMatch() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    @Test
    void testMultiMatch() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        request.source().query(QueryBuilders.multiMatchQuery("脱脂牛奶", "name", "category"));
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    @Test
    void testTerm() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        request.source().query(QueryBuilders.termQuery("brand", "华为"));
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    @Test
    void testBool() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        // 2.1.准备bool查询
        BoolQueryBuilder bool = QueryBuilders.boolQuery();
        // 2.2.关键字搜索
        bool.must(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        // 2.3.品牌过滤
        bool.filter(QueryBuilders.termQuery("brand", "德亚"));
        // 2.4.价格过滤
        bool.filter(QueryBuilders.rangeQuery("price").lte(30000));
        request.source().query(bool);
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    @Test
    void testPageAndSort() throws IOException {
        int pageNo = 1, pageSize = 5;

        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        // 2.1.搜索条件参数
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        // 2.2.排序参数
        request.source().sort("price", SortOrder.ASC);
        // 2.3.分页参数
        request.source().from((pageNo - 1) * pageSize).size(pageSize);
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    @Test
    void testAgg() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.准备请求参数
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("category", "手机"))
                .filter(QueryBuilders.rangeQuery("price").gte(300000));
        request.source().query(bool).size(0);
        // 3.聚合参数
        request.source().aggregation(
                AggregationBuilders.terms("brand_agg").field("brand").size(5)
        );
        // 4.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 5.解析聚合结果
        Aggregations aggregations = response.getAggregations();
        // 5.1.获取品牌聚合
        Terms brandTerms = aggregations.get("brand_agg");
        // 5.2.获取聚合中的桶
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        // 5.3.遍历桶内数据
        for (Terms.Bucket bucket : buckets) {
            // 5.4.获取桶内key
            String brand = bucket.getKeyAsString();
            System.out.print("brand = " + brand);
            long count = bucket.getDocCount();
            System.out.println("; count = " + count);
        }
    }

    @Test
    public void searchItemsPage() throws IOException {
        ItemPageQuery itemPageQuery = new ItemPageQuery();
        itemPageQuery.setBrand("小米");
        itemPageQuery.setCategory("手机");
        itemPageQuery.setMinPrice(10000);
        itemPageQuery.setMaxPrice(29900);
        itemPageQuery.setKey("手机");
        itemPageQuery.setPageNo(1);
        itemPageQuery.setPageSize(20);

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
        PageDTO<ItemDoc> itemDocPageDTO = PageDTO.of(itemDocPage, ItemDoc.class);
        System.out.println(itemDocPageDTO);
    }

    @Test
    public void filtersItem() throws IOException {
        ItemPageQuery itemPageQuery = new ItemPageQuery();
        itemPageQuery.setBrand("小米");
        itemPageQuery.setCategory("手机");
        itemPageQuery.setMinPrice(10000);
        itemPageQuery.setMaxPrice(29900);
        itemPageQuery.setKey("手机");
        itemPageQuery.setPageNo(1);
        itemPageQuery.setPageSize(20);

        // 创建BoolQueryBuilder
        BoolQueryBuilder boolQuery = new BoolQueryBuilder();

        // 添加必须匹配的条件
        if (StrUtil.isNotBlank(itemPageQuery.getKey())) {
            boolQuery.must(new MatchQueryBuilder("name", itemPageQuery.getKey()));
        }

        // 构建搜索源构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(boolQuery)
                // 添加排序
                .sort(new FieldSortBuilder("sold").order(SortOrder.DESC))
                // 设置每页大小和起始位置
                .size(0);

        // 创建搜索请求对象
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("items"); // 指定索引名
        searchRequest.source(sourceBuilder);
        searchRequest.source()
                .aggregation(
                        AggregationBuilders.terms("category_agg").field("category"))
                .aggregation(
                        AggregationBuilders.terms("brand_agg").field("brand"));

        // 执行搜索请求
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

        // 5.解析聚合结果
        Aggregations aggregations = response.getAggregations();
        Map<String, List<String>> resultMap = new HashMap<>();
        Terms categoryTerms = aggregations.get("category_agg");
        if (categoryTerms!=null){
            List<? extends Terms.Bucket> buckets = categoryTerms.getBuckets();
            resultMap.put("category", buckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
        }
        Terms brandTerms = aggregations.get("brand_agg");
        if (categoryTerms!=null){
            List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
            resultMap.put("brand", buckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
        }
        System.out.println(resultMap);
    }

    @AfterEach
    void tearDown() throws IOException {
        this.client.close();
    }
}
