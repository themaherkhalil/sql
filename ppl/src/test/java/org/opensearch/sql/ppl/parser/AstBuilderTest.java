/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opensearch.sql.ppl.parser;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.opensearch.sql.ast.dsl.AstDSL.agg;
import static org.opensearch.sql.ast.dsl.AstDSL.aggregate;
import static org.opensearch.sql.ast.dsl.AstDSL.alias;
import static org.opensearch.sql.ast.dsl.AstDSL.argument;
import static org.opensearch.sql.ast.dsl.AstDSL.booleanLiteral;
import static org.opensearch.sql.ast.dsl.AstDSL.compare;
import static org.opensearch.sql.ast.dsl.AstDSL.dedupe;
import static org.opensearch.sql.ast.dsl.AstDSL.defaultDedupArgs;
import static org.opensearch.sql.ast.dsl.AstDSL.defaultFieldsArgs;
import static org.opensearch.sql.ast.dsl.AstDSL.defaultSortFieldArgs;
import static org.opensearch.sql.ast.dsl.AstDSL.defaultStatsArgs;
import static org.opensearch.sql.ast.dsl.AstDSL.eval;
import static org.opensearch.sql.ast.dsl.AstDSL.exprList;
import static org.opensearch.sql.ast.dsl.AstDSL.field;
import static org.opensearch.sql.ast.dsl.AstDSL.filter;
import static org.opensearch.sql.ast.dsl.AstDSL.function;
import static org.opensearch.sql.ast.dsl.AstDSL.head;
import static org.opensearch.sql.ast.dsl.AstDSL.intLiteral;
import static org.opensearch.sql.ast.dsl.AstDSL.let;
import static org.opensearch.sql.ast.dsl.AstDSL.map;
import static org.opensearch.sql.ast.dsl.AstDSL.nullLiteral;
import static org.opensearch.sql.ast.dsl.AstDSL.projectWithArg;
import static org.opensearch.sql.ast.dsl.AstDSL.qualifiedName;
import static org.opensearch.sql.ast.dsl.AstDSL.rareTopN;
import static org.opensearch.sql.ast.dsl.AstDSL.relation;
import static org.opensearch.sql.ast.dsl.AstDSL.rename;
import static org.opensearch.sql.ast.dsl.AstDSL.sort;
import static org.opensearch.sql.ast.dsl.AstDSL.span;
import static org.opensearch.sql.ast.dsl.AstDSL.stringLiteral;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.sql.ast.Node;
import org.opensearch.sql.ast.expression.SpanUnit;
import org.opensearch.sql.ast.tree.RareTopN.CommandType;
import org.opensearch.sql.ppl.antlr.PPLSyntaxParser;

public class AstBuilderTest {

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  private PPLSyntaxParser parser = new PPLSyntaxParser();

  @Test
  public void testSearchCommand() {
    assertEqual("search source=t a=1",
        filter(
            relation("t"),
            compare("=", field("a"), intLiteral(1))
        )
    );
  }

  @Test
  public void testSearchCommandString() {
    assertEqual("search source=t a=\"a\"",
        filter(
            relation("t"),
            compare("=", field("a"), stringLiteral("a"))
        )
    );
  }

  @Test
  public void testSearchCommandWithoutSearch() {
    assertEqual("source=t a=1",
        filter(
            relation("t"),
            compare("=", field("a"), intLiteral(1))
        )
    );
  }

  @Test
  public void testSearchCommandWithFilterBeforeSource() {
    assertEqual("search a=1 source=t",
        filter(
            relation("t"),
            compare("=", field("a"), intLiteral(1))
        ));
  }

  @Test
  public void testWhereCommand() {
    assertEqual("search source=t | where a=1",
        filter(
            relation("t"),
            compare("=", field("a"), intLiteral(1))
        )
    );
  }

  @Test
  public void testWhereCommandWithQualifiedName() {
    assertEqual("search source=t | where a.v=1",
        filter(
            relation("t"),
            compare("=", field(qualifiedName("a", "v")), intLiteral(1))
        )
    );
  }

  @Test
  public void testFieldsCommandWithoutArguments() {
    assertEqual("source=t | fields f, g",
        projectWithArg(
            relation("t"),
            defaultFieldsArgs(),
            field("f"), field("g")
        ));
  }

  @Test
  public void testFieldsCommandWithIncludeArguments() {
    assertEqual("source=t | fields + f, g",
        projectWithArg(
            relation("t"),
            defaultFieldsArgs(),
            field("f"), field("g")
        ));
  }

  @Test
  public void testFieldsCommandWithExcludeArguments() {
    assertEqual("source=t | fields - f, g",
        projectWithArg(
            relation("t"),
            exprList(argument("exclude", booleanLiteral(true))),
            field("f"), field("g")
        ));
  }

  @Test
  public void testSearchCommandWithQualifiedName() {
    assertEqual("source=t | fields f.v, g.v",
        projectWithArg(
            relation("t"),
            defaultFieldsArgs(),
            field(qualifiedName("f", "v")), field(qualifiedName("g", "v"))
        ));
  }

  @Test
  public void testRenameCommand() {
    assertEqual("source=t | rename f as g",
        rename(
            relation("t"),
            map("f", "g")
        ));
  }

  @Test
  public void testRenameCommandWithMultiFields() {
    assertEqual("source=t | rename f as g, h as i, j as k",
        rename(
            relation("t"),
            map("f", "g"),
            map("h", "i"),
            map("j", "k")
        ));
  }

  @Test
  public void testStatsCommand() {
    assertEqual("source=t | stats count(a)",
        agg(
            relation("t"),
            exprList(
                alias(
                    "count(a)",
                    aggregate("count", field("a"))
                )
            ),
            emptyList(),
            emptyList(),
            defaultStatsArgs()
        ));
  }

  @Test
  public void testStatsCommandWithByClause() {
    assertEqual("source=t | stats count(a) by b DEDUP_SPLITVALUES=false",
        agg(
            relation("t"),
            exprList(
                alias(
                    "count(a)",
                    aggregate("count", field("a"))
                )
            ),
            emptyList(),
            exprList(
                alias(
                    "b",
                    field("b")
                )),
            defaultStatsArgs()
        ));
  }

  @Test
  public void testStatsCommandWithAlias() {
    assertEqual("source=t | stats count(a) as alias",
        agg(
            relation("t"),
            exprList(
                alias(
                    "alias",
                    aggregate("count", field("a"))
                )
            ),
            emptyList(),
            emptyList(),
            defaultStatsArgs()
        )
    );
  }

  @Test
  public void testStatsCommandWithNestedFunctions() {
    assertEqual("source=t | stats sum(a+b)",
        agg(
            relation("t"),
            exprList(
                alias(
                    "sum(a+b)",
                    aggregate(
                        "sum",
                        function("+", field("a"), field("b"))
                    ))
            ),
            emptyList(),
            emptyList(),
            defaultStatsArgs()
        ));
    assertEqual("source=t | stats sum(abs(a)/2)",
        agg(
            relation("t"),
            exprList(
                alias(
                    "sum(abs(a)/2)",
                    aggregate(
                        "sum",
                        function(
                            "/",
                            function("abs", field("a")),
                            intLiteral(2)
                        )
                    )
                )
            ),
            emptyList(),
            emptyList(),
            defaultStatsArgs()
        ));
  }

  @Test
  public void testStatsCommandWithSpan() {
    assertEqual("source=t | stats avg(price) by span(timestamp, 1h)",
        agg(
            relation("t"),
            exprList(
                alias("avg(price)", aggregate("avg", field("price")))
            ),
            emptyList(),
            exprList(
                alias("span(timestamp,1h)", span(field("timestamp"), intLiteral(1), SpanUnit.H))
            ),
            defaultStatsArgs()
        ));

    assertEqual("source=t | stats count(a) by span(age, 10)",
        agg(
            relation("t"),
            exprList(
                alias("count(a)", aggregate("count", field("a")))
            ),
            emptyList(),
            exprList(
                alias("span(age,10)", span(field("age"), intLiteral(10), SpanUnit.NONE))
            ),
            defaultStatsArgs()
        ));
  }

  @Test
  public void testStatsSpanWithAlias() {
    assertEqual("source=t | stats avg(price) by span(timestamp, 1h) as time_span",
        agg(
            relation("t"),
            exprList(
                alias("avg(price)", aggregate("avg", field("price")))
            ),
            emptyList(),
            exprList(
                alias("span(timestamp,1h)", span(
                    field("timestamp"), intLiteral(1), SpanUnit.H), "time_span")
            ),
            defaultStatsArgs()
        ));

    assertEqual("source=t | stats count(a) by span(age, 10) as numeric_span",
        agg(
            relation("t"),
            exprList(
                alias("count(a)", aggregate("count", field("a")))
            ),
            emptyList(),
            exprList(alias("span(age,10)", span(
                field("age"), intLiteral(10), SpanUnit.NONE), "numeric_span")
            ),
            defaultStatsArgs()
        ));
  }

  @Test
  public void testDedupCommand() {
    assertEqual("source=t | dedup f1, f2",
        dedupe(
            relation("t"),
            defaultDedupArgs(),
            field("f1"), field("f2")
        ));
  }

  /**
   * disable sortby from the dedup command syntax.
   */
  @Ignore(value = "disable sortby from the dedup command syntax")
  public void testDedupCommandWithSortby() {
    assertEqual("source=t | dedup f1, f2 sortby f3",
        agg(
            relation("t"),
            exprList(field("f1"), field("f2")),
            exprList(field("f3", defaultSortFieldArgs())),
            null,
            defaultDedupArgs()
        ));
  }

  @Test
  public void testHeadCommand() {
    assertEqual("source=t | head",
        head(relation("t"), 10));
  }

  @Test
  public void testHeadCommandWithNumber() {
    assertEqual("source=t | head 3",
        head(relation("t"), 3));
  }

  @Test
  public void testSortCommand() {
    assertEqual("source=t | sort f1, f2",
        sort(
            relation("t"),
            field("f1", defaultSortFieldArgs()),
            field("f2", defaultSortFieldArgs())
        ));
  }

  @Test
  public void testSortCommandWithOptions() {
    assertEqual("source=t | sort - f1, + f2",
        sort(
            relation("t"),
            field("f1", exprList(argument("asc", booleanLiteral(false)),
                argument("type", nullLiteral()))),
            field("f2", defaultSortFieldArgs())
        ));
  }

  @Test
  public void testEvalCommand() {
    assertEqual("source=t | eval r=abs(f)",
        eval(
            relation("t"),
            let(
                field("r"),
                function("abs", field("f"))
            )
        ));
  }

  @Test
  public void testIndexName() {
    assertEqual("source=`log.2020.04.20.` a=1",
        filter(
            relation("log.2020.04.20."),
            compare("=", field("a"), intLiteral(1))
        ));
  }

  @Test
  public void testIdentifierAsIndexNameStartWithDot() {
    assertEqual("source=.opensearch_dashboards",
        relation(".opensearch_dashboards"));
  }

  @Test
  public void testIdentifierAsIndexNameWithDotInTheMiddle() {
    assertEqual("source=log.2020.10.10", relation("log.2020.10.10"));
    assertEqual("source=log-7.10-2020.10.10", relation("log-7.10-2020.10.10"));
  }

  @Test
  public void testIdentifierAsIndexNameWithSlashInTheMiddle() {
    assertEqual("source=log-2020",
        relation("log-2020"));
  }

  @Test
  public void testIdentifierAsIndexNameContainStar() {
    assertEqual("source=log-2020-10-*",
        relation("log-2020-10-*"));
  }

  @Test
  public void testIdentifierAsIndexNameContainStarAndDots() {
    assertEqual("source=log-2020.10.*", relation("log-2020.10.*"));
    assertEqual("source=log-2020.*.01", relation("log-2020.*.01"));
    assertEqual("source=log-2020.*.*", relation("log-2020.*.*"));
  }

  @Test
  public void testIdentifierAsFieldNameStartWithAt() {
    assertEqual("source=log-2020 | fields @timestamp",
        projectWithArg(
            relation("log-2020"),
            defaultFieldsArgs(),
            field("@timestamp")
        ));
  }

  @Test
  public void testRareCommand() {
    assertEqual("source=t | rare a",
        rareTopN(
            relation("t"),
            CommandType.RARE,
            exprList(argument("noOfResults", intLiteral(10))),
            emptyList(),
            field("a")
        ));
  }

  @Test
  public void testRareCommandWithGroupBy() {
    assertEqual("source=t | rare a by b",
        rareTopN(
            relation("t"),
            CommandType.RARE,
            exprList(argument("noOfResults", intLiteral(10))),
            exprList(field("b")),
            field("a")
        ));
  }

  @Test
  public void testRareCommandWithMultipleFields() {
    assertEqual("source=t | rare `a`, `b` by `c`",
        rareTopN(
            relation("t"),
            CommandType.RARE,
            exprList(argument("noOfResults", intLiteral(10))),
            exprList(field("c")),
            field("a"),
            field("b")
        ));
  }

  @Test
  public void testTopCommandWithN() {
    assertEqual("source=t | top 1 a",
        rareTopN(
            relation("t"),
            CommandType.TOP,
            exprList(argument("noOfResults", intLiteral(1))),
            emptyList(),
            field("a")
        ));
  }

  @Test
  public void testTopCommandWithoutNAndGroupBy() {
    assertEqual("source=t | top a",
        rareTopN(
            relation("t"),
            CommandType.TOP,
            exprList(argument("noOfResults", intLiteral(10))),
            emptyList(),
            field("a")
        ));
  }

  @Test
  public void testTopCommandWithNAndGroupBy() {
    assertEqual("source=t | top 1 a by b",
        rareTopN(
            relation("t"),
            CommandType.TOP,
            exprList(argument("noOfResults", intLiteral(1))),
            exprList(field("b")),
            field("a")
        ));
  }

  @Test
  public void testTopCommandWithMultipleFields() {
    assertEqual("source=t | top 1 `a`, `b` by `c`",
        rareTopN(
            relation("t"),
            CommandType.TOP,
            exprList(argument("noOfResults", intLiteral(1))),
            exprList(field("c")),
            field("a"),
            field("b")
        ));
  }

  protected void assertEqual(String query, Node expectedPlan) {
    Node actualPlan = plan(query);
    assertEquals(expectedPlan, actualPlan);
  }

  protected void assertEqual(String query, String expected) {
    Node expectedPlan = plan(expected);
    assertEqual(query, expectedPlan);
  }

  private Node plan(String query) {
    AstBuilder astBuilder = new AstBuilder(new AstExpressionBuilder(), query);
    return astBuilder.visit(parser.analyzeSyntax(query));
  }
}
