package com.amazon.opendistroforelasticsearch.ppl.planner.dsl;

import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SearchRequestBuilder {
    private final String index;
    private final QueryBuilder queryBuilder;
    private final SourceFilter sourceFilter;

    public SearchRequest build() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.size(1);
        sourceBuilder.from(0);

        sourceBuilder.fetchSource(sourceFilter.getIncludes(), sourceFilter.getExcludes());
        sourceBuilder.query(queryBuilder);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(index);
        searchRequest.source(sourceBuilder);

        return searchRequest;
    }

}
