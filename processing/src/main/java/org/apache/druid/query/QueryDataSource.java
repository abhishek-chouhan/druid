/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.query.planning.DataSourceAnalysis;
import org.apache.druid.segment.SegmentReference;
import org.apache.druid.utils.DatasourceUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@JsonTypeName("query")
public class QueryDataSource implements DataSource
{
  @JsonProperty
  private final Query<?> query;

  @JsonCreator
  public QueryDataSource(@JsonProperty("query") Query query)
  {
    this.query = Preconditions.checkNotNull(query, "'query' must be nonnull");
  }

  @Override
  public Set<String> getTableNames()
  {
    return query.getDataSource().getTableNames();
  }

  @JsonProperty
  public Query getQuery()
  {
    return query;
  }

  @Override
  public List<DataSource> getChildren()
  {
    return Collections.singletonList(query.getDataSource());
  }

  @Override
  public DataSource withChildren(List<DataSource> children)
  {
    if (children.size() != 1) {
      throw new IAE("Must have exactly one child");
    }

    return new QueryDataSource(query.withDataSource(Iterables.getOnlyElement(children)));
  }

  @Override
  public boolean isCacheable(boolean isBroker)
  {
    return false;
  }

  @Override
  public boolean isGlobal()
  {
    return query.getDataSource().isGlobal();
  }

  @Override
  public boolean isConcrete()
  {
    return false;
  }

  @Override
  public Function<SegmentReference, SegmentReference> createSegmentMapFunction(
      Query query,
      AtomicLong cpuTime
  )
  {
    final Query<?> subQuery = this.getQuery();
    return subQuery.getDataSource().createSegmentMapFunction(subQuery, cpuTime);
  }

  @Override
  public DataSource withUpdatedDataSource(DataSource newSource)
  {
    return new QueryDataSource(query.withDataSource(query.getDataSource().withUpdatedDataSource(newSource)));
  }

  @Override
  public byte[] getCacheKey()
  {
    return null;
  }

  @Override
  public DataSourceAnalysis getAnalysis()
  {
    final Query<?> subQuery = this.getQuery();
    if (!(subQuery instanceof BaseQuery)) {
      // We must verify that the subQuery is a BaseQuery, because it is required to make
      // "DataSourceAnalysis.getBaseQuerySegmentSpec" work properly.
      // All built-in query types are BaseQuery, so we only expect this with funky extension queries.
      throw new IAE("Cannot analyze subquery of class[%s]", subQuery.getClass().getName());
    }
    final DataSource current = subQuery.getDataSource();
    return current.getAnalysis().maybeWithBaseQuery(subQuery);
  }

  @Override
  public boolean hasTimeFilter()
  {
    return query.getDataSource().hasTimeFilter() || DatasourceUtils.queryHasTimeFilter(query);
  }

  @Override
  public String toString()
  {
    return query.toString();
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    QueryDataSource that = (QueryDataSource) o;

    if (!query.equals(that.query)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    return query.hashCode();
  }
}
