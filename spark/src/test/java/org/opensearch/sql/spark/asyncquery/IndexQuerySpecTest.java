/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.spark.asyncquery;

import com.amazonaws.services.emrserverless.model.CancelJobRunResult;
import com.amazonaws.services.emrserverless.model.GetJobRunResult;
import com.amazonaws.services.emrserverless.model.JobRun;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.opensearch.sql.spark.asyncquery.model.AsyncQueryExecutionResponse;
import org.opensearch.sql.spark.asyncquery.model.MockFlintIndex;
import org.opensearch.sql.spark.asyncquery.model.MockFlintSparkJob;
import org.opensearch.sql.spark.client.EMRServerlessClientFactory;
import org.opensearch.sql.spark.client.StartJobRequest;
import org.opensearch.sql.spark.flint.FlintIndexState;
import org.opensearch.sql.spark.flint.FlintIndexType;
import org.opensearch.sql.spark.leasemanager.ConcurrencyLimitExceededException;
import org.opensearch.sql.spark.rest.model.CreateAsyncQueryRequest;
import org.opensearch.sql.spark.rest.model.CreateAsyncQueryResponse;
import org.opensearch.sql.spark.rest.model.LangType;

public class IndexQuerySpecTest extends AsyncQueryExecutorServiceSpec {
  private final String specialName = "`test ,:\"+/\\|?#><`";
  private final String encodedName = "test%20%2c%3a%22%2b%2f%5c%7c%3f%23%3e%3c";

  public final String REFRESH_SI = "REFRESH SKIPPING INDEX on mys3.default.http_logs";
  public final String REFRESH_CI = "REFRESH INDEX covering ON mys3.default.http_logs";
  public final String REFRESH_MV = "REFRESH MATERIALIZED VIEW mys3.default.http_logs_metrics";
  public final String REFRESH_SCI = "REFRESH SKIPPING INDEX on mys3.default." + specialName;

  public final FlintDatasetMock LEGACY_SKIPPING =
      new FlintDatasetMock(
              "DROP SKIPPING INDEX ON mys3.default.http_logs",
              REFRESH_SI,
              FlintIndexType.SKIPPING,
              "flint_mys3_default_http_logs_skipping_index")
          .isLegacy(true);
  public final FlintDatasetMock LEGACY_COVERING =
      new FlintDatasetMock(
              "DROP INDEX covering ON mys3.default.http_logs",
              REFRESH_CI,
              FlintIndexType.COVERING,
              "flint_mys3_default_http_logs_covering_index")
          .isLegacy(true);
  public final FlintDatasetMock LEGACY_MV =
      new FlintDatasetMock(
              "DROP MATERIALIZED VIEW mys3.default.http_logs_metrics",
              REFRESH_MV,
              FlintIndexType.MATERIALIZED_VIEW,
              "flint_mys3_default_http_logs_metrics")
          .isLegacy(true);

  public final FlintDatasetMock LEGACY_SPECIAL_CHARACTERS =
      new FlintDatasetMock(
              "DROP SKIPPING INDEX ON mys3.default." + specialName,
              REFRESH_SCI,
              FlintIndexType.SKIPPING,
              "flint_mys3_default_" + encodedName + "_skipping_index")
          .isLegacy(true)
          .isSpecialCharacter(true);

  public final FlintDatasetMock SKIPPING =
      new FlintDatasetMock(
              "DROP SKIPPING INDEX ON mys3.default.http_logs",
              REFRESH_SI,
              FlintIndexType.SKIPPING,
              "flint_mys3_default_http_logs_skipping_index")
          .latestId("ZmxpbnRfbXlzM19kZWZhdWx0X2h0dHBfbG9nc19za2lwcGluZ19pbmRleA==");
  public final FlintDatasetMock COVERING =
      new FlintDatasetMock(
              "DROP INDEX covering ON mys3.default.http_logs",
              REFRESH_CI,
              FlintIndexType.COVERING,
              "flint_mys3_default_http_logs_covering_index")
          .latestId("ZmxpbnRfbXlzM19kZWZhdWx0X2h0dHBfbG9nc19jb3ZlcmluZ19pbmRleA==");
  public final FlintDatasetMock MV =
      new FlintDatasetMock(
              "DROP MATERIALIZED VIEW mys3.default.http_logs_metrics",
              REFRESH_MV,
              FlintIndexType.MATERIALIZED_VIEW,
              "flint_mys3_default_http_logs_metrics")
          .latestId("ZmxpbnRfbXlzM19kZWZhdWx0X2h0dHBfbG9nc19tZXRyaWNz");
  public final FlintDatasetMock SPECIAL_CHARACTERS =
      new FlintDatasetMock(
              "DROP SKIPPING INDEX ON mys3.default." + specialName,
              REFRESH_SCI,
              FlintIndexType.SKIPPING,
              "flint_mys3_default_" + encodedName + "_skipping_index")
          .isSpecialCharacter(true)
          .latestId(
              "ZmxpbnRfbXlzM19kZWZhdWx0X3Rlc3QlMjAlMmMlM2ElMjIlMmIlMmYlNWMlN2MlM2YlMjMlM2UlM2Nfc2tpcHBpbmdfaW5kZXg=");

  public final String CREATE_SI_AUTO =
      "CREATE SKIPPING INDEX ON mys3.default.http_logs"
          + "(l_orderkey VALUE_SET) WITH (auto_refresh = true)";

  public final String CREATE_CI_AUTO =
      "CREATE INDEX covering ON mys3.default.http_logs "
          + "(l_orderkey, l_quantity) WITH (auto_refresh = true)";

  public final String CREATE_MV_AUTO =
      "CREATE MATERIALIZED VIEW mys3.default.http_logs_metrics AS select * "
          + "from mys3.default.https WITH (auto_refresh = true)";

  /**
   * Happy case. expectation is
   *
   * <p>(1) Drop Index response is SUCCESS
   */
  @Test
  public void legacyBasicDropAndFetchAndCancel() {
    ImmutableList.of(LEGACY_SKIPPING, LEGACY_COVERING, LEGACY_SPECIAL_CHARACTERS)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Cancelled"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();

              // 1.drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              assertNotNull(response.getQueryId());
              assertTrue(clusterService.state().routingTable().hasIndex(mockDS.indexName));

              // 2.fetch result
              AsyncQueryExecutionResponse asyncQueryResults =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryResults.getStatus());
              assertNull(asyncQueryResults.getError());
              emrsClient.cancelJobRunCalled(1);

              // 3.cancel
              IllegalArgumentException exception =
                  assertThrows(
                      IllegalArgumentException.class,
                      () -> asyncQueryExecutorService.cancelQuery(response.getQueryId()));
              assertEquals("can't cancel index DML query", exception.getMessage());
            });
  }

  /**
   * Legacy Test, without state index support. Not EMR-S job running. expectation is
   *
   * <p>(1) Drop Index response is SUCCESS
   */
  @Test
  public void legacyDropIndexNoJobRunning() {
    ImmutableList.of(LEGACY_SKIPPING, LEGACY_COVERING, LEGACY_MV, LEGACY_SPECIAL_CHARACTERS)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
                      throw new IllegalArgumentException("Job run is not in a cancellable state");
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();

              // 1.drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2.fetch result.
              AsyncQueryExecutionResponse asyncQueryResults =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryResults.getStatus());
              assertNull(asyncQueryResults.getError());
            });
  }

  /**
   * Legacy Test, without state index support. Cancel EMR-S job call timeout. expectation is
   *
   * <p>(1) Drop Index response is FAILED
   */
  @Test
  public void legacyDropIndexCancelJobTimeout() {
    ImmutableList.of(LEGACY_SKIPPING, LEGACY_COVERING, LEGACY_MV, LEGACY_SPECIAL_CHARACTERS)
        .forEach(
            mockDS -> {
              // Mock EMR-S always return running.
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Running"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryResults =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryResults.getStatus());
              assertEquals("Cancel job operation timed out.", asyncQueryResults.getError());
            });
  }

  /**
   * Legacy Test, without state index support. Not EMR-S job running. expectation is
   *
   * <p>(1) Drop Index response is SUCCESS
   */
  @Test
  public void legacyDropIndexSpecialCharacter() {
    FlintDatasetMock mockDS = LEGACY_SPECIAL_CHARACTERS;
    LocalEMRSClient emrsClient =
        new LocalEMRSClient() {
          @Override
          public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
            throw new IllegalArgumentException("Job run is not in a cancellable state");
          }
        };
    EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
    AsyncQueryExecutorService asyncQueryExecutorService =
        createAsyncQueryExecutorService(emrServerlessClientFactory);

    // Mock flint index
    mockDS.createIndex();

    // 1.drop index
    CreateAsyncQueryResponse response =
        asyncQueryExecutorService.createAsyncQuery(
            new CreateAsyncQueryRequest(mockDS.query, MYGLUE_DATASOURCE, LangType.SQL, null));

    // 2.fetch result.
    AsyncQueryExecutionResponse asyncQueryResults =
        asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
    assertEquals("SUCCESS", asyncQueryResults.getStatus());
    assertNull(asyncQueryResults.getError());
  }

  /**
   * Happy case. expectation is
   *
   * <p>(1) Drop Index response is SUCCESS (2) change index state to: DELETED
   */
  @Test
  public void dropAndFetchAndCancel() {
    ImmutableList.of(SKIPPING, COVERING, MV, SPECIAL_CHARACTERS)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Cancelled"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);
              flintIndexJob.refreshing();

              // 1.drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              assertNotNull(response.getQueryId());
              assertTrue(clusterService.state().routingTable().hasIndex(mockDS.indexName));

              // assert state is DELETED
              flintIndexJob.assertState(FlintIndexState.DELETED);

              // 2.fetch result
              AsyncQueryExecutionResponse asyncQueryResults =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryResults.getStatus());
              assertNull(asyncQueryResults.getError());
              emrsClient.cancelJobRunCalled(1);

              // 3.cancel
              IllegalArgumentException exception =
                  assertThrows(
                      IllegalArgumentException.class,
                      () -> asyncQueryExecutorService.cancelQuery(response.getQueryId()));
              assertEquals("can't cancel index DML query", exception.getMessage());
            });
  }

  /**
   * Cancel EMR-S job, but not job running. expectation is
   *
   * <p>(1) Drop Index response is SUCCESS (2) change index state to: DELETED
   */
  @Test
  public void dropIndexNoJobRunning() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              // Mock EMR-S job is not running
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
                      throw new IllegalArgumentException("Job run is not in a cancellable state");
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state in refresh state.
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);
              flintIndexJob.refreshing();

              // 1.drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2.fetch result.
              AsyncQueryExecutionResponse asyncQueryResults =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryResults.getStatus());
              assertNull(asyncQueryResults.getError());

              flintIndexJob.assertState(FlintIndexState.DELETED);
            });
  }

  /**
   * Cancel EMR-S job call timeout, expectation is
   *
   * <p>(1) Drop Index response is failed, (2) change index state to: CANCELLING
   */
  @Test
  public void dropIndexCancelJobTimeout() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              // Mock EMR-S always return running.
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Running"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);
              flintIndexJob.refreshing();

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryResults =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryResults.getStatus());
              assertEquals("Cancel job operation timed out.", asyncQueryResults.getError());
              flintIndexJob.assertState(FlintIndexState.REFRESHING);
            });
  }

  /**
   * Drop Index operation is retryable, expectation is
   *
   * <p>(1) call EMR-S (2) change index state to: DELETED
   */
  @Test
  public void dropIndexWithIndexInRefreshingState() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Cancelled"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);
              flintIndexJob.refreshing();

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              assertEquals(
                  "SUCCESS",
                  asyncQueryExecutorService
                      .getAsyncQueryResults(response.getQueryId())
                      .getStatus());

              flintIndexJob.assertState(FlintIndexState.DELETED);
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(1);
              emrsClient.getJobRunResultCalled(1);
            });
  }

  /**
   * Index state is stable, Drop Index operation is retryable, expectation is
   *
   * <p>(1) call EMR-S (2) change index state to: DELETED
   */
  @Test
  public void dropIndexWithIndexInActiveState() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Cancelled"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryExecutionResponse.getStatus());
              flintIndexJob.assertState(FlintIndexState.DELETED);
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(1);
              emrsClient.getJobRunResultCalled(1);
            });
  }

  /**
   * Index state is stable, expectation is
   *
   * <p>(1) call EMR-S (2) change index state to: DELETED
   */
  @Test
  public void dropIndexWithIndexInCreatingState() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Cancelled"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);
              flintIndexJob.creating();

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              assertEquals(
                  "SUCCESS",
                  asyncQueryExecutorService
                      .getAsyncQueryResults(response.getQueryId())
                      .getStatus());

              flintIndexJob.assertState(FlintIndexState.DELETED);
            });
  }

  /**
   * Index state is stable, Drop Index operation is retryable, expectation is
   *
   * <p>(1) call EMR-S (2) change index state to: DELETED
   */
  @Test
  public void dropIndexWithIndexInEmptyState() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      return new GetJobRunResult().withJobRun(new JobRun().withState("Cancelled"));
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              assertEquals(
                  "SUCCESS",
                  asyncQueryExecutorService
                      .getAsyncQueryResults(response.getQueryId())
                      .getStatus());

              flintIndexJob.assertState(FlintIndexState.DELETED);
            });
  }

  /**
   * Couldn't acquire lock as the index is in transitioning state. Will result in error.
   *
   * <p>(1) not call EMR-S (2) change index state to: DELETED
   */
  @Test
  public void dropIndexWithIndexInDeletedState() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
                      Assert.fail("should not call cancelJobRun");
                      return null;
                    }

                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      Assert.fail("should not call getJobRunResult");
                      return null;
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);
              flintIndexJob.deleting();

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              // 2. fetch result
              assertEquals("FAILED", asyncQueryExecutionResponse.getStatus());
              assertEquals(
                  "Transaction failed as flint index is not in a valid state.",
                  asyncQueryExecutionResponse.getError());
              flintIndexJob.assertState(FlintIndexState.DELETING);
            });
  }

  /**
   * Cancel EMR-S job, but not job running. expectation is
   *
   * <p>(1) Drop Index response is SUCCESS (2) change index state to: DELETED
   */
  @Test
  public void dropIndexSpecialCharacter() {
    FlintDatasetMock mockDS = SPECIAL_CHARACTERS;
    // Mock EMR-S job is not running
    LocalEMRSClient emrsClient =
        new LocalEMRSClient() {
          @Override
          public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
            throw new IllegalArgumentException("Job run is not in a cancellable state");
          }
        };
    EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
    AsyncQueryExecutorService asyncQueryExecutorService =
        createAsyncQueryExecutorService(emrServerlessClientFactory);

    // Mock flint index
    mockDS.createIndex();
    // Mock index state in refresh state.
    MockFlintSparkJob flintIndexJob =
        new MockFlintSparkJob(stateStore, mockDS.latestId, MYGLUE_DATASOURCE);
    flintIndexJob.refreshing();

    // 1.drop index
    CreateAsyncQueryResponse response =
        asyncQueryExecutorService.createAsyncQuery(
            new CreateAsyncQueryRequest(mockDS.query, MYGLUE_DATASOURCE, LangType.SQL, null));

    // 2.fetch result.
    AsyncQueryExecutionResponse asyncQueryResults =
        asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
    assertEquals("SUCCESS", asyncQueryResults.getStatus());
    assertNull(asyncQueryResults.getError());

    flintIndexJob.assertState(FlintIndexState.DELETED);
  }

  /**
   * No Job running, expectation is
   *
   * <p>(1) not call EMR-S (2) change index state to: DELETED
   */
  @Test
  public void edgeCaseNoIndexStateDoc() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
                      Assert.fail("should not call cancelJobRun");
                      return null;
                    }

                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      Assert.fail("should not call getJobRunResult");
                      return null;
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // Mock flint index
              mockDS.createIndex();

              // 1. drop index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryResults =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryResults.getStatus());
              assertTrue(asyncQueryResults.getError().contains("no state found"));
            });
  }

  @Test
  public void concurrentRefreshJobLimitNotApplied() {
    EMRServerlessClientFactory emrServerlessClientFactory = new LocalEMRServerlessClientFactory();
    AsyncQueryExecutorService asyncQueryExecutorService =
        createAsyncQueryExecutorService(emrServerlessClientFactory);

    // Mock flint index
    COVERING.createIndex();
    // Mock index state
    MockFlintSparkJob flintIndexJob =
        new MockFlintSparkJob(stateStore, COVERING.latestId, MYS3_DATASOURCE);
    flintIndexJob.refreshing();

    // query with auto refresh
    String query =
        "CREATE INDEX covering ON mys3.default.http_logs(l_orderkey, "
            + "l_quantity) WITH (auto_refresh = true)";
    CreateAsyncQueryResponse response =
        asyncQueryExecutorService.createAsyncQuery(
            new CreateAsyncQueryRequest(query, MYS3_DATASOURCE, LangType.SQL, null));
    assertNull(response.getSessionId());
  }

  @Test
  public void concurrentRefreshJobLimitAppliedToDDLWithAuthRefresh() {
    EMRServerlessClientFactory emrServerlessClientFactory = new LocalEMRServerlessClientFactory();
    AsyncQueryExecutorService asyncQueryExecutorService =
        createAsyncQueryExecutorService(emrServerlessClientFactory);

    setConcurrentRefreshJob(1);

    // Mock flint index
    COVERING.createIndex();
    // Mock index state
    MockFlintSparkJob flintIndexJob =
        new MockFlintSparkJob(stateStore, COVERING.latestId, MYS3_DATASOURCE);
    flintIndexJob.refreshing();

    // query with auto_refresh = true.
    String query =
        "CREATE INDEX covering ON mys3.default.http_logs(l_orderkey, "
            + "l_quantity) WITH (auto_refresh = true)";
    ConcurrencyLimitExceededException exception =
        assertThrows(
            ConcurrencyLimitExceededException.class,
            () ->
                asyncQueryExecutorService.createAsyncQuery(
                    new CreateAsyncQueryRequest(query, MYS3_DATASOURCE, LangType.SQL, null)));
    assertEquals("domain concurrent refresh job can not exceed 1", exception.getMessage());
  }

  @Test
  public void concurrentRefreshJobLimitAppliedToRefresh() {
    EMRServerlessClientFactory emrServerlessClientFactory = new LocalEMRServerlessClientFactory();
    AsyncQueryExecutorService asyncQueryExecutorService =
        createAsyncQueryExecutorService(emrServerlessClientFactory);

    setConcurrentRefreshJob(1);

    // Mock flint index
    COVERING.createIndex();
    // Mock index state
    MockFlintSparkJob flintIndexJob =
        new MockFlintSparkJob(stateStore, COVERING.latestId, MYS3_DATASOURCE);
    flintIndexJob.refreshing();

    // query with auto_refresh = true.
    String query = "REFRESH INDEX covering ON mys3.default.http_logs";
    ConcurrencyLimitExceededException exception =
        assertThrows(
            ConcurrencyLimitExceededException.class,
            () ->
                asyncQueryExecutorService.createAsyncQuery(
                    new CreateAsyncQueryRequest(query, MYS3_DATASOURCE, LangType.SQL, null)));
    assertEquals("domain concurrent refresh job can not exceed 1", exception.getMessage());
  }

  @Test
  public void concurrentRefreshJobLimitNotAppliedToDDL() {
    String query = "CREATE INDEX covering ON mys3.default.http_logs(l_orderkey, l_quantity)";
    EMRServerlessClientFactory emrServerlessClientFactory = new LocalEMRServerlessClientFactory();
    AsyncQueryExecutorService asyncQueryExecutorService =
        createAsyncQueryExecutorService(emrServerlessClientFactory);

    setConcurrentRefreshJob(1);

    // Mock flint index
    COVERING.createIndex();
    // Mock index state
    MockFlintSparkJob flintIndexJob =
        new MockFlintSparkJob(stateStore, COVERING.latestId, MYS3_DATASOURCE);
    flintIndexJob.refreshing();

    CreateAsyncQueryResponse asyncQueryResponse =
        asyncQueryExecutorService.createAsyncQuery(
            new CreateAsyncQueryRequest(query, MYS3_DATASOURCE, LangType.SQL, null));
    assertNotNull(asyncQueryResponse.getSessionId());
  }

  /** Cancel create flint index statement with auto_refresh=true, should throw exception. */
  @Test
  public void cancelAutoRefreshCreateFlintIndexShouldThrowException() {
    ImmutableList.of(CREATE_SI_AUTO, CREATE_CI_AUTO, CREATE_MV_AUTO)
        .forEach(
            query -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
                      Assert.fail("should not call cancelJobRun");
                      return null;
                    }

                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      Assert.fail("should not call getJobRunResult");
                      return null;
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);

              // 1. submit create / refresh index query
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(query, MYS3_DATASOURCE, LangType.SQL, null));

              // 2. cancel query
              IllegalArgumentException exception =
                  assertThrows(
                      IllegalArgumentException.class,
                      () -> asyncQueryExecutorService.cancelQuery(response.getQueryId()));
              assertEquals(
                  "can't cancel index DML query, using ALTER auto_refresh=off statement to stop"
                      + " job, using VACUUM statement to stop job and delete data",
                  exception.getMessage());
            });
  }

  /** Cancel REFRESH statement should success */
  @Test
  public void cancelRefreshStatement() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(
                      () ->
                          new LocalEMRSClient() {
                            @Override
                            public GetJobRunResult getJobRunResult(
                                String applicationId, String jobId) {
                              return new GetJobRunResult()
                                  .withJobRun(new JobRun().withState("Cancelled"));
                            }
                          });

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);

              // 1. Submit REFRESH statement
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.refreshQuery, MYS3_DATASOURCE, LangType.SQL, null));
              // mock index state.
              flintIndexJob.refreshing();

              // 2. Cancel query
              String cancelResponse = asyncQueryExecutorService.cancelQuery(response.getQueryId());

              assertNotNull(cancelResponse);
              assertTrue(clusterService.state().routingTable().hasIndex(mockDS.indexName));

              // assert state is active
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
            });
  }

  /** Cancel REFRESH statement should success */
  @Test
  public void cancelRefreshStatementWithActiveState() {
    ImmutableList.of(SKIPPING, COVERING, MV)
        .forEach(
            mockDS -> {
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(
                      () ->
                          new LocalEMRSClient() {
                            @Override
                            public GetJobRunResult getJobRunResult(
                                String applicationId, String jobId) {
                              return new GetJobRunResult()
                                  .withJobRun(new JobRun().withState("Cancelled"));
                            }
                          });

              // Mock flint index
              mockDS.createIndex();
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.latestId, MYS3_DATASOURCE);

              // 1. Submit REFRESH statement
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.refreshQuery, MYS3_DATASOURCE, LangType.SQL, null));
              // mock index state.
              flintIndexJob.active();

              // 2. Cancel query
              IllegalStateException illegalStateException =
                  Assertions.assertThrows(
                      IllegalStateException.class,
                      () -> asyncQueryExecutorService.cancelQuery(response.getQueryId()));
              Assertions.assertEquals(
                  "Transaction failed as flint index is not in a valid state.",
                  illegalStateException.getMessage());

              // assert state is active
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
            });
  }

  @Test
  public void cancelRefreshStatementWithFailureInFetchingIndexMetadata() {
    String indexName = "flint_my_glue_mydb_http_logs_covering_corrupted_index";
    MockFlintIndex mockFlintIndex =
        new MockFlintIndex(client(), indexName, FlintIndexType.COVERING, null);
    AsyncQueryExecutorService asyncQueryExecutorService =
        createAsyncQueryExecutorService(
            () ->
                new LocalEMRSClient() {
                  @Override
                  public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                    return new GetJobRunResult().withJobRun(new JobRun().withState("Cancelled"));
                  }
                });

    mockFlintIndex.createIndex();
    // Mock index state
    MockFlintSparkJob flintIndexJob =
        new MockFlintSparkJob(stateStore, indexName + "_latest_id", MYS3_DATASOURCE);

    // 1. Submit REFRESH statement
    CreateAsyncQueryResponse response =
        asyncQueryExecutorService.createAsyncQuery(
            new CreateAsyncQueryRequest(
                "REFRESH INDEX covering_corrupted ON my_glue.mydb.http_logs",
                MYS3_DATASOURCE,
                LangType.SQL,
                null));
    // mock index state.
    flintIndexJob.refreshing();

    // 2. Cancel query
    Assertions.assertThrows(
        IllegalStateException.class,
        () -> asyncQueryExecutorService.cancelQuery(response.getQueryId()));
  }

  @Test
  public void testAlterIndexQueryConvertingToManualRefresh() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false,"
                + " incremental_refresh=false) ");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING, ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              mockDS.updateIndexOptions(existingOptions, false);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryExecutionResponse.getStatus());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(1);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("false", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryConvertingToManualRefreshWithNoIncrementalRefresh() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=false)");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH (auto_refresh=false)");
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false)");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING, ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              existingOptions.put("checkpoint_location", "s3://checkpoint/location");
              mockDS.updateIndexOptions(existingOptions, true);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryExecutionResponse.getStatus());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(1);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("false", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryWithRedundantOperation() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false,"
                + " incremental_refresh=false) ");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING, ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public String startJobRun(StartJobRequest startJobRequest) {
                      return "jobId";
                    }

                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }

                    @Override
                    public CancelJobRunResult cancelJobRun(String applicationId, String jobId) {
                      super.cancelJobRun(applicationId, jobId);
                      throw new IllegalArgumentException("JobId doesn't exist");
                    }
                  };
              EMRServerlessClientFactory emrServerlessCientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessCientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "false");
              mockDS.updateIndexOptions(existingOptions, false);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryExecutionResponse.getStatus());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(1);
              emrsClient.getJobRunResultCalled(0);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("false", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryConvertingToAutoRefresh() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=true,"
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH (auto_refresh=true,"
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=true,"
                + " incremental_refresh=false) ");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING, ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient localEMRSClient = new LocalEMRSClient();
              EMRServerlessClientFactory clientFactory = () -> localEMRSClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(clientFactory);

              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "false");
              mockDS.updateIndexOptions(existingOptions, false);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              assertEquals(
                  "RUNNING",
                  asyncQueryExecutorService
                      .getAsyncQueryResults(response.getQueryId())
                      .getStatus());

              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              localEMRSClient.startJobRunCalled(1);
              localEMRSClient.getJobRunResultCalled(1);
              localEMRSClient.cancelJobRunCalled(0);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("false", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryWithOutAnyAutoRefresh() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH ("
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH ("
                + " incremental_refresh=false)");
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (" + " incremental_refresh=false) ");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING, ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient localEMRSClient = new LocalEMRSClient();
              EMRServerlessClientFactory clientFactory = () -> localEMRSClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(clientFactory);

              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "false");
              mockDS.updateIndexOptions(existingOptions, false);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              assertEquals(
                  "RUNNING",
                  asyncQueryExecutorService
                      .getAsyncQueryResults(response.getQueryId())
                      .getStatus());

              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              localEMRSClient.startJobRunCalled(1);
              localEMRSClient.getJobRunResultCalled(1);
              localEMRSClient.cancelJobRunCalled(0);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("false", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryOfFullRefreshWithInvalidOptions() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=false, checkpoint_location=\"s3://ckp/skp\")");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=false, checkpoint_location=\"s3://ckp/skp\")");
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false,"
                + " incremental_refresh=false, checkpoint_location=\"s3://ckp/skp\") ");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING, ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              mockDS.updateIndexOptions(existingOptions, false);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryExecutionResponse.getStatus());
              assertEquals(
                  "Altering to full refresh only allows: [auto_refresh, incremental_refresh]"
                      + " options",
                  asyncQueryExecutionResponse.getError());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(0);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("true", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryOfIncrementalRefreshWithInvalidOptions() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=true, output_mode=\"complete\")");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=true, output_mode=\"complete\")");
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false,"
                + " incremental_refresh=true, output_mode=\"complete\") ");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING, ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              mockDS.updateIndexOptions(existingOptions, false);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryExecutionResponse.getStatus());
              assertEquals(
                  "Altering to incremental refresh only allows: [auto_refresh, incremental_refresh,"
                      + " watermark_delay, checkpoint_location] options",
                  asyncQueryExecutionResponse.getError());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(0);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("true", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryOfIncrementalRefreshWithInsufficientOptions() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=true)");
    MockFlintIndex ALTER_COVERING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_covering_index",
            FlintIndexType.COVERING,
            "ALTER INDEX covering ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=true)");
    ImmutableList.of(ALTER_SKIPPING, ALTER_COVERING)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              existingOptions.put("incremental_refresh", "false");
              mockDS.updateIndexOptions(existingOptions, true);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryExecutionResponse.getStatus());
              assertEquals(
                  "Conversion to incremental refresh index cannot proceed due to missing"
                      + " attributes: checkpoint_location.",
                  asyncQueryExecutionResponse.getError());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(0);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("true", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryOfIncrementalRefreshWithInsufficientOptionsForMV() {
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false,"
                + " incremental_refresh=true) ");
    ImmutableList.of(ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              existingOptions.put("incremental_refresh", "false");
              mockDS.updateIndexOptions(existingOptions, true);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryExecutionResponse.getStatus());
              assertEquals(
                  "Conversion to incremental refresh index cannot proceed due to missing"
                      + " attributes: checkpoint_location, watermark_delay.",
                  asyncQueryExecutionResponse.getError());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(0);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("true", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryOfIncrementalRefreshWithEmptyExistingOptionsForMV() {
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false,"
                + " incremental_refresh=true) ");
    ImmutableList.of(ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              existingOptions.put("incremental_refresh", "false");
              existingOptions.put("watermark_delay", "");
              existingOptions.put("checkpoint_location", "");
              mockDS.updateIndexOptions(existingOptions, true);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.active();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryExecutionResponse.getStatus());
              assertEquals(
                  "Conversion to incremental refresh index cannot proceed due to missing"
                      + " attributes: checkpoint_location, watermark_delay.",
                  asyncQueryExecutionResponse.getError());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(0);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("true", options.get("auto_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryOfIncrementalRefresh() {
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false,"
                + " incremental_refresh=true) ");
    ImmutableList.of(ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              existingOptions.put("incremental_refresh", "false");
              existingOptions.put("watermark_delay", "watermark_delay");
              existingOptions.put("checkpoint_location", "s3://checkpoint/location");
              mockDS.updateIndexOptions(existingOptions, true);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.refreshing();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryExecutionResponse.getStatus());
              emrsClient.startJobRunCalled(0);
              emrsClient.getJobRunResultCalled(1);
              emrsClient.cancelJobRunCalled(1);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("false", options.get("auto_refresh"));
              Assertions.assertEquals("true", options.get("incremental_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryWithIncrementalRefreshAlreadyExisting() {
    MockFlintIndex ALTER_MV =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_mv",
            FlintIndexType.MATERIALIZED_VIEW,
            "ALTER MATERIALIZED VIEW my_glue.mydb.mv WITH (auto_refresh=false) ");
    ImmutableList.of(ALTER_MV)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              existingOptions.put("incremental_refresh", "true");
              existingOptions.put("watermark_delay", "watermark_delay");
              existingOptions.put("checkpoint_location", "s3://checkpoint/location");
              mockDS.updateIndexOptions(existingOptions, true);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.refreshing();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("SUCCESS", asyncQueryExecutionResponse.getStatus());
              emrsClient.startJobRunCalled(0);
              emrsClient.getJobRunResultCalled(1);
              emrsClient.cancelJobRunCalled(1);
              flintIndexJob.assertState(FlintIndexState.ACTIVE);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("false", options.get("auto_refresh"));
              Assertions.assertEquals("true", options.get("incremental_refresh"));
            });
  }

  @Test
  public void testAlterIndexQueryWithInvalidInitialState() {
    MockFlintIndex ALTER_SKIPPING =
        new MockFlintIndex(
            client,
            "flint_my_glue_mydb_http_logs_skipping_index",
            FlintIndexType.SKIPPING,
            "ALTER SKIPPING INDEX ON my_glue.mydb.http_logs WITH (auto_refresh=false,"
                + " incremental_refresh=false)");
    ImmutableList.of(ALTER_SKIPPING)
        .forEach(
            mockDS -> {
              LocalEMRSClient emrsClient =
                  new LocalEMRSClient() {
                    @Override
                    public GetJobRunResult getJobRunResult(String applicationId, String jobId) {
                      super.getJobRunResult(applicationId, jobId);
                      JobRun jobRun = new JobRun();
                      jobRun.setState("cancelled");
                      return new GetJobRunResult().withJobRun(jobRun);
                    }
                  };
              EMRServerlessClientFactory emrServerlessClientFactory = () -> emrsClient;
              AsyncQueryExecutorService asyncQueryExecutorService =
                  createAsyncQueryExecutorService(emrServerlessClientFactory);
              // Mock flint index
              mockDS.createIndex();
              HashMap<String, Object> existingOptions = new HashMap<>();
              existingOptions.put("auto_refresh", "true");
              mockDS.updateIndexOptions(existingOptions, false);
              // Mock index state
              MockFlintSparkJob flintIndexJob =
                  new MockFlintSparkJob(stateStore, mockDS.getLatestId(), MYS3_DATASOURCE);
              flintIndexJob.updating();

              // 1. alter index
              CreateAsyncQueryResponse response =
                  asyncQueryExecutorService.createAsyncQuery(
                      new CreateAsyncQueryRequest(
                          mockDS.getQuery(), MYS3_DATASOURCE, LangType.SQL, null));

              // 2. fetch result
              AsyncQueryExecutionResponse asyncQueryExecutionResponse =
                  asyncQueryExecutorService.getAsyncQueryResults(response.getQueryId());
              assertEquals("FAILED", asyncQueryExecutionResponse.getStatus());
              assertEquals(
                  "Transaction failed as flint index is not in a valid state.",
                  asyncQueryExecutionResponse.getError());
              emrsClient.startJobRunCalled(0);
              emrsClient.cancelJobRunCalled(0);
              flintIndexJob.assertState(FlintIndexState.UPDATING);
              Map<String, Object> mappings = mockDS.getIndexMappings();
              Map<String, Object> meta = (HashMap<String, Object>) mappings.get("_meta");
              Map<String, Object> options = (Map<String, Object>) meta.get("options");
              Assertions.assertEquals("true", options.get("auto_refresh"));
            });
  }
}
