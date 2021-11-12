/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 *
 *    Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License").
 *    You may not use this file except in compliance with the License.
 *    A copy of the License is located at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    or in the "license" file accompanying this file. This file is distributed
 *    on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language governing
 *    permissions and limitations under the License.
 *
 */

package org.opensearch.sql.opensearch.storage.script.sort;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opensearch.sql.data.type.ExprCoreType.INTEGER;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.opensearch.sql.ast.tree.Sort;
import org.opensearch.sql.expression.DSL;
import org.opensearch.sql.expression.config.ExpressionConfig;

class SortQueryBuilderTest {

  private final DSL dsl = new ExpressionConfig().dsl(new ExpressionConfig().functionRepository());

  private SortQueryBuilder sortQueryBuilder = new SortQueryBuilder();

  @Test
  void build_sortbuilder_from_reference() {
    assertNotNull(sortQueryBuilder.build(DSL.ref("intV", INTEGER), Sort.SortOption.DEFAULT_ASC));
  }

  @Test
  void build_sortbuilder_from_function_should_throw_exception() {
    final IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> sortQueryBuilder.build(dsl.equal(DSL.ref(
            "intV", INTEGER), DSL.literal(1)), Sort.SortOption.DEFAULT_ASC));
    assertThat(exception.getMessage(), Matchers.containsString("unsupported expression"));
  }
}
