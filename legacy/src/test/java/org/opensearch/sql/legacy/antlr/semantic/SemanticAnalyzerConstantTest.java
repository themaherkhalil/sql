/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 *    Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License").
 *    You may not use this file except in compliance with the License.
 *    A copy of the License is located at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    or in the "license" file accompanying this file. This file is distributed
 *    on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language governing
 *    permissions and limitations under the License.
 *
 */

package org.opensearch.sql.legacy.antlr.semantic;

import org.junit.Test;

public class SemanticAnalyzerConstantTest extends SemanticAnalyzerTestBase {

    @Test
    public void useNegativeIntegerShouldPass() {
        validate("SELECT * FROM test WHERE age > -1");
    }

    @Test
    public void useNegativeFloatingPointNumberShouldPass() {
        validate("SELECT * FROM test WHERE balance > -1.23456");
    }

}
