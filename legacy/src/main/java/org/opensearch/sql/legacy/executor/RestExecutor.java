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

package org.opensearch.sql.legacy.executor;

import java.util.Map;
import org.opensearch.client.Client;
import org.opensearch.rest.RestChannel;
import org.opensearch.sql.legacy.query.QueryAction;

/**
 * Created by Eliran on 26/12/2015.
 */
public interface RestExecutor {
    void execute(Client client, Map<String, String> params, QueryAction queryAction, RestChannel channel)
            throws Exception;

    String execute(Client client, Map<String, String> params, QueryAction queryAction) throws Exception;
}
