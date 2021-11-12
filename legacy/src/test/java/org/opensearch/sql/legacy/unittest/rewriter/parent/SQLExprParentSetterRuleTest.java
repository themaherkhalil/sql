/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package org.opensearch.sql.legacy.unittest.rewriter.parent;

import static org.junit.Assert.assertTrue;

import com.alibaba.druid.sql.ast.expr.SQLQueryExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opensearch.sql.legacy.rewriter.parent.SQLExprParentSetterRule;

@RunWith(MockitoJUnitRunner.class)
public class SQLExprParentSetterRuleTest {

    @Mock
    private SQLQueryExpr queryExpr;

    private SQLExprParentSetterRule rule = new SQLExprParentSetterRule();

    @Test
    public void match() {
        assertTrue(rule.match(queryExpr));
    }
}
