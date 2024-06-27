package com.hmall.item.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ItemListener {

    private final RestHighLevelClient client;
    private final IItemService itemService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "item.item.index.queue", durable = "true"),
            exchange = @Exchange(name = "search.direct", type = ExchangeTypes.DIRECT),
            key = "item.index"
    ))
    public void listenItemIndex(Long id) {
        Item item = itemService.getById(id);
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
        String doc = JSONUtil.toJsonStr(itemDoc);
        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
        request.source(doc, XContentType.JSON);
        try {
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "item.item.delete.queue", durable = "true"),
            exchange = @Exchange(name = "search.direct", type = ExchangeTypes.DIRECT),
            key = "item.delete"
    ))
    public void listenItemDelete(Long id) {
        DeleteRequest request = new DeleteRequest("items", String.valueOf(id));
        try {
            client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "item.stock.detuct.queue", durable = "true"),
            exchange = @Exchange(name = "stock.direct", type = ExchangeTypes.DIRECT),
            key = "stock.detuct"
    ))
    public void listenStockDetuct(List<OrderDetailDTO> items) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        for (OrderDetailDTO item : items) {
            UpdateRequest updateRequest = new UpdateRequest("items", String.valueOf(item.getItemId()));
            // 设置脚本
            Script script = new Script(
                    ScriptType.INLINE,
                    "painless",
                    "ctx._source.stock -= params.decrease",
                    Collections.singletonMap("decrease", item.getNum()) // 参数中使用的
            );
            updateRequest.script(script);
            bulkRequest.add(updateRequest);
        }
        client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "item.stock.restore.queue", durable = "true"),
            exchange = @Exchange(name = "stock.direct", type = ExchangeTypes.DIRECT),
            key = "stock.restore"
    ))
    public void listenStockRestore(List<OrderDetailDTO> items) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        for (OrderDetailDTO item : items) {
            UpdateRequest updateRequest = new UpdateRequest("items", String.valueOf(item.getItemId()));
            // 设置脚本
            Script script = new Script(
                    ScriptType.INLINE,
                    "painless",
                    "ctx._source.stock += params.restore",
                    Collections.singletonMap("restore", item.getNum())
            );
            updateRequest.script(script);
            bulkRequest.add(updateRequest);
        }
        client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }


}
