package com.hmall.search.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.service.IItemSearchService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ItemSearchService implements IItemSearchService {

    private RestHighLevelClient client;

    @Override
    public ItemDoc getItemById(Long id) {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.139.10:9200")
        ));
        // 1.准备Request对象
        GetRequest request = new GetRequest("items").id("100002644680");
        // 2.发送请求
        GetResponse response = null;
        try {
            response = client.get(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                this.client.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // 3.获取响应结果中的source
        String json = response.getSourceAsString();

        return JSONUtil.toBean(json, ItemDoc.class);
    }
}
