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

package org.opensearch.sql.opensearch.planner.logical;

import java.util.Arrays;
import lombok.experimental.UtilityClass;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeAggAndIndexScan;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeAggAndRelation;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeFilterAndRelation;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeLimitAndIndexScan;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeLimitAndRelation;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeSortAndIndexAgg;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeSortAndIndexScan;
import org.opensearch.sql.opensearch.planner.logical.rule.MergeSortAndRelation;
import org.opensearch.sql.opensearch.planner.logical.rule.PushProjectAndIndexScan;
import org.opensearch.sql.opensearch.planner.logical.rule.PushProjectAndRelation;
import org.opensearch.sql.planner.optimizer.LogicalPlanOptimizer;

/**
 * OpenSearch storage specified logical plan optimizer.
 */
@UtilityClass
public class OpenSearchLogicalPlanOptimizerFactory {

  /**
   * Create OpenSearch storage specified logical plan optimizer.
   */
  public static LogicalPlanOptimizer create() {
    return new LogicalPlanOptimizer(Arrays.asList(
        new MergeFilterAndRelation(),
        new MergeAggAndIndexScan(),
        new MergeAggAndRelation(),
        new MergeSortAndRelation(),
        new MergeSortAndIndexScan(),
        new MergeSortAndIndexAgg(),
        new MergeSortAndIndexScan(),
        new MergeLimitAndRelation(),
        new MergeLimitAndIndexScan(),
        new PushProjectAndRelation(),
        new PushProjectAndIndexScan()
    ));
  }
}
