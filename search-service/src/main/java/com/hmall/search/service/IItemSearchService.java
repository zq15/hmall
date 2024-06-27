package com.hmall.search.service;

import com.hmall.search.domain.po.ItemDoc;

public interface IItemSearchService {
    ItemDoc getItemById(Long id);
}
