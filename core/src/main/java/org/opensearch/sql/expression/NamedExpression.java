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

package org.opensearch.sql.expression;

import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.opensearch.sql.data.model.ExprValue;
import org.opensearch.sql.data.type.ExprType;
import org.opensearch.sql.expression.env.Environment;

/**
 * Named expression that represents expression with name.
 * Please see more details in associated unresolved expression operator
 * {@link org.opensearch.sql.ast.expression.Alias}.
 */
@AllArgsConstructor
@EqualsAndHashCode
@Getter
@RequiredArgsConstructor
public class NamedExpression implements Expression {

  /**
   * Expression name.
   */
  private final String name;

  /**
   * Expression that being named.
   */
  private final Expression delegated;

  /**
   * Optional alias.
   */
  private String alias;

  @Override
  public ExprValue valueOf(Environment<Expression, ExprValue> valueEnv) {
    return delegated.valueOf(valueEnv);
  }

  @Override
  public ExprType type() {
    return delegated.type();
  }

  /**
   * Get expression name using name or its alias (if it's present).
   * @return  expression name
   */
  public String getNameOrAlias() {
    return Strings.isNullOrEmpty(alias) ? name : alias;
  }

  @Override
  public <T, C> T accept(ExpressionNodeVisitor<T, C> visitor, C context) {
    return visitor.visitNamed(this, context);
  }

  @Override
  public String toString() {
    return getNameOrAlias();
  }

}
