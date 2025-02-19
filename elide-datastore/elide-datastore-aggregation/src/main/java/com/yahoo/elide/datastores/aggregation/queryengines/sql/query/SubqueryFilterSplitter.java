/*
 * Copyright 2021, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */

package com.yahoo.elide.datastores.aggregation.queryengines.sql.query;

import com.yahoo.elide.core.filter.expression.AndFilterExpression;
import com.yahoo.elide.core.filter.expression.FilterExpression;
import com.yahoo.elide.core.filter.expression.FilterExpressionVisitor;
import com.yahoo.elide.core.filter.expression.NotFilterExpression;
import com.yahoo.elide.core.filter.expression.OrFilterExpression;
import com.yahoo.elide.core.filter.predicates.FilterPredicate;
import com.yahoo.elide.core.filter.visitors.FilterExpressionNormalizationVisitor;
import com.yahoo.elide.core.type.Type;
import com.yahoo.elide.datastores.aggregation.metadata.MetaDataStore;
import com.yahoo.elide.datastores.aggregation.queryengines.sql.metadata.SQLReferenceTable;
import com.yahoo.elide.datastores.aggregation.queryengines.sql.metadata.SQLTable;

import org.apache.commons.collections4.CollectionUtils;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Splits a filter expression into two parts:
 * 1. A part that can be safely run on a nested subquery where no joins take place.
 * 2. A part that must run on the outer query because of joins to other tables.
 */
public class SubqueryFilterSplitter
        implements FilterExpressionVisitor<SubqueryFilterSplitter.SplitFilter> {

    @Data
    @Builder
    public static class SplitFilter {
        FilterExpression outer;
        FilterExpression inner;
    }

    private SQLReferenceTable lookupTable;
    private MetaDataStore metaDataStore;

    public SubqueryFilterSplitter(MetaDataStore metaDataStore, SQLReferenceTable lookupTable) {
        this.metaDataStore = metaDataStore;
        this.lookupTable = lookupTable;
    }

    public static SplitFilter splitFilter(
            SQLReferenceTable lookupTable,
            MetaDataStore metaDataStore,
            FilterExpression expression) {

        if (expression == null) {
            return SplitFilter.builder().build();
        }

        FilterExpressionNormalizationVisitor normalizer = new FilterExpressionNormalizationVisitor();
        FilterExpression normalizedExpression = expression.accept(normalizer);

        return normalizedExpression.accept(new SubqueryFilterSplitter(metaDataStore, lookupTable));
    }

    @Override
    public SplitFilter visitPredicate(FilterPredicate filterPredicate) {
        Type<?> tableType = filterPredicate.getEntityType();
        String fieldName = filterPredicate.getField();

        SQLTable table = (SQLTable) metaDataStore.getTable(tableType);

        Set<String> joins = lookupTable.getResolvedJoinExpressions(table, fieldName);

        if (CollectionUtils.isNotEmpty(joins)) {
            return SplitFilter.builder().outer(filterPredicate).build();
        } else {
            return SplitFilter.builder().inner(filterPredicate).build();
        }
    }

    @Override
    public SplitFilter visitAndExpression(AndFilterExpression expression) {
        SplitFilter lhs = expression.getLeft().accept(this);
        SplitFilter rhs = expression.getRight().accept(this);

        return SplitFilter.builder()
                .outer(AndFilterExpression.fromPair(lhs.getOuter(), rhs.getOuter()))
                .inner(AndFilterExpression.fromPair(lhs.getInner(), rhs.getInner()))
                .build();
    }

    @Override
    public SplitFilter visitOrExpression(OrFilterExpression expression) {
        SplitFilter lhs = expression.getLeft().accept(this);
        SplitFilter rhs = expression.getRight().accept(this);

        //If either the left or right side of the expression require an outer query, the entire
        //expression must be run as an outer query.
        if (lhs.getOuter() != null || rhs.getOuter() != null) {

            //The only operation that splits a filter into inner & outer is AND.  We can recombine
            //each side using AND and then OR the results.
            FilterExpression combined = OrFilterExpression.fromPair(
                    AndFilterExpression.fromPair(lhs.getOuter(), lhs.getInner()),
                    AndFilterExpression.fromPair(rhs.getOuter(), rhs.getInner()));

            return SplitFilter.builder()
                    .outer(combined)
                    .build();

        //Both left and right are inner queries.
        } else {
            FilterExpression combined = OrFilterExpression.fromPair(
                    lhs.getInner(),
                    rhs.getInner());

            return SplitFilter.builder()
                    .inner(combined)
                    .build();
        }
    }

    @Override
    public SplitFilter visitNotExpression(NotFilterExpression expression) {
        SplitFilter negated = expression.getNegated().accept(this);

        FilterExpression outerFilter = negated.getOuter() == null
                ? null
                : new NotFilterExpression(negated.getOuter());

        FilterExpression innerFilter = negated.getInner() == null
                ? null
                : new NotFilterExpression(negated.getInner());

        return SplitFilter.builder()
                .outer(outerFilter)
                .inner(innerFilter)
                .build();
    }
}
