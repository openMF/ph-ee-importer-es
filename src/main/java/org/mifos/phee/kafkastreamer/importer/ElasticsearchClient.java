package org.mifos.phee.kafkastreamer.importer;

import io.prometheus.client.Histogram;
import io.zeebe.exporter.ElasticsearchExporterException;
import io.zeebe.exporter.ElasticsearchMetrics;
import io.zeebe.util.VersionUtil;
import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

@Component
public class ElasticsearchClient {
    public static String INDEX_TEMPLATE_FILENAME_PATTERN = "/zeebe-record-%s-template.json";
    public static String INDEX_DELIMITER = "_";
    public static String ALIAS_DELIMITER = "-";

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${importer.elasticsearch.bulk-size}")
    private Integer bulkSize;

    @Value("${importer.elasticsearch.url}")
    private String elasticUrl;

    @Value("${importer.elasticsearch.index-prefix}")
    private String indexPrefix;

    @Value("${reporting.enabled}")
    private Boolean reportingEnabled;

    @Autowired
    private TaskScheduler taskScheduler;

    private RestHighLevelClient client;
    private ElasticsearchMetrics metrics;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private BulkRequest bulkRequest = new BulkRequest();

    @PostConstruct
    public void setup() {
        this.client = createClient();
        taskScheduler.schedule(this::flush, new PeriodicTrigger(2000));
    }

    public void close() throws IOException {
        client.close();
    }

    public void bulk(IndexRequest indexRequest) {
        logger.info("Calling bulk request for insert");
        bulkRequest.add(indexRequest);
    }
    public void bulk(UpdateRequest updateRequest) {
        logger.info("Calling bulk request for upsert");
        bulkRequest.add(updateRequest);
    }

    public void index(JSONObject record) {
        if (metrics == null) {
            metrics = new ElasticsearchMetrics(record.getInt("partitionId"));
        }
        logger.info("Getting index method called with record value type " + record.getString("valueType"));
        if (reportingEnabled) {
            upsertToReportingIndex(record);
        }
            logger.info("Pushing index for " + indexFor(record));
            IndexRequest request =
                    new IndexRequest(indexFor(record), typeFor(record), idFor(record))
                            .source(record.toString(), XContentType.JSON)
                            .routing(Integer.toString(record.getInt("partitionId")));
            bulk(request);
        }

    public void upsertToReportingIndex(JSONObject record){
        JSONObject newRecord = new JSONObject();
        if (record.getString("valueType").equalsIgnoreCase("variable")) {
            JSONObject valueObj = record.getJSONObject("value");
            if (valueObj.has("name")) {
                if (!valueObj.getString("name").contains("Request") && !valueObj.getString("name")
                        .contains("Body") && !valueObj.getString("name").contains("json")) {
                    if (valueObj.getString("name").equalsIgnoreCase("amount")) {
                        newRecord.put((String) valueObj.get("name"),
                                Double.parseDouble(valueObj.getString("value").replaceAll("\"",
                                        "")));
                    } else if (valueObj.getString("name").equalsIgnoreCase("originDate")) {
                        Instant timestamp = Instant.ofEpochMilli(valueObj.getLong("value"));
                        newRecord.put((String) valueObj.get("name"), timestamp);
                    } else
                        newRecord.put((String) valueObj.get("name"), valueObj.get("value").toString()
                                .replaceAll("\"", ""));
                }
                if (!newRecord.has("processInstanceKey"))
                    newRecord.put("processInstanceKey",
                            String.valueOf(valueObj.getLong("processInstanceKey")));
                Instant timestamp = Instant.ofEpochMilli(record.getLong("timestamp"));
                newRecord.put("timestamp", timestamp);
            }
            logger.info("New Record before insert is: " + newRecord);
            String version = VersionUtil.getVersionLowerCase();
            Instant timestamp = Instant.ofEpochMilli(record.getLong("timestamp"));
            String name = "zeebe-payments" + INDEX_DELIMITER + version + INDEX_DELIMITER +
                    formatter.format(timestamp);
            if (!newRecord.has("initiator") || !newRecord.has("isNotificationsFailureEnabled") ||
                    !newRecord.has("isNotificationsSuccessEnabled") || !newRecord.has("mpesaTransactionId")
                    || !newRecord.has("partyLookupFailed") || !newRecord.has("tenantId") ||
                    !newRecord.has("timer") || !newRecord.has("transactionId") ||
                    !newRecord.has("transferCreateFailed") || !newRecord.has("getTransactionStatusHttpCode")
                    || !newRecord.has("getTransactionStatusHttpCode") || !newRecord.has("errorCode") ||
                    !newRecord.has("getTransactionStatusResponse") || !newRecord.has("isCallbackReceived")
                    || !newRecord.has("transferResponse-CREATE")
            ) {
                UpdateRequest request1 = new UpdateRequest(name, valueObj.get("processInstanceKey").toString())
                        .doc(newRecord.toMap())
                        .upsert(newRecord.toString(), XContentType.JSON);
                bulk(request1);
            }
        }
    }
    public synchronized int flush() {
        boolean success;
        int bulkSize = bulkRequest.numberOfActions();
        if (bulkSize > 0) {
            try {
                metrics.recordBulkSize(bulkSize);
                BulkResponse responses = exportBulk();
                success = checkBulkResponses(responses);
            } catch (IOException e) {
                throw new ElasticsearchExporterException("Failed to flush bulk", e);
            }

            if (success) { // all records where flushed, create new bulk request, otherwise retry next time
                bulkRequest = new BulkRequest();
            }
        }

        return bulkSize;
    }

    private BulkResponse exportBulk() throws IOException {
        try (Histogram.Timer timer = metrics.measureFlushDuration()) {
            return client.bulk(bulkRequest, RequestOptions.DEFAULT);
        }
    }

    private boolean checkBulkResponses(BulkResponse responses) {
        for (BulkItemResponse response : responses) {
            if (response.isFailed()) {
                logger.warn("Failed to flush at least one bulk request {}", response.getFailureMessage());
                return false;
            }
        }

        return true;
    }

    public boolean shouldFlush() {
        return bulkRequest.numberOfActions() >= bulkSize;
    }

    /**
     * @return true if request was acknowledged
     */
    public boolean putIndexTemplate(ExtendedValueType extendedValueType) {
        String templateName = indexPrefixForValueType(extendedValueType);
        String aliasName = aliasNameForValueType(extendedValueType);
        String filename = indexTemplateForValueType(extendedValueType);
        return putIndexTemplate(templateName, aliasName, filename);
    }

    /**
     * @return true if request was acknowledged
     */
    public boolean putIndexTemplate(
            String templateName, String aliasName, String filename) {
        Map<String, Object> template;
        try (InputStream inputStream =
                     KafkaElasticImportApplication.class.getResourceAsStream(filename)) {
            if (inputStream != null) {
                template = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
            } else {
                throw new ElasticsearchExporterException(
                        "Failed to find index template in classpath " + filename);
            }
        } catch (IOException e) {
            throw new ElasticsearchExporterException(
                    "Failed to load index template from classpath " + filename, e);
        }

        // update prefix in template in case it was changed in configuration
        template.put("index_patterns", Collections.singletonList(templateName + INDEX_DELIMITER + "*"));

        // update alias in template in case it was changed in configuration
        template.put("aliases", Collections.singletonMap(aliasName, Collections.EMPTY_MAP));

        if(reportingEnabled) {
            if (templateName.equals("zeebe-record")) {
                Map<String, Object> template1;
                try (InputStream inputStream1 =
                             KafkaElasticImportApplication.class.getResourceAsStream("/zeebe-payments.json")) {
                    if (inputStream1 != null) {
                        template1 = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream1, true);
                    } else {
                        throw new ElasticsearchExporterException(
                                "Failed to find index template in classpath " + filename);
                    }
                } catch (IOException e) {
                    throw new ElasticsearchExporterException(
                            "Failed to load index template from classpath " + filename, e);
                }
                template1.put("index_patterns", Collections.singletonList("zeebe-payments" + INDEX_DELIMITER + "*"));
                template1.put("aliases", Collections.singletonMap("zeebe-payments", Collections.EMPTY_MAP));
                PutIndexTemplateRequest request = new PutIndexTemplateRequest("zeebe-payments").source(template1);
                putIndexTemplate(request);
            }
        }
        PutIndexTemplateRequest request =
                new PutIndexTemplateRequest(templateName).source(template);

        return putIndexTemplate(request);
    }

    /**
     * @return true if request was acknowledged
     */
    private boolean putIndexTemplate(PutIndexTemplateRequest putIndexTemplateRequest) {
        try {
            return client
                    .indices()
                    .putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT)
                    .isAcknowledged();
        } catch (IOException e) {
            throw new ElasticsearchExporterException("Failed to put index template", e);
        }
    }

    private RestHighLevelClient createClient() {
        HttpHost httpHost = urlToHttpHost(elasticUrl);

        // use single thread for rest client
        RestClientBuilder builder =
                RestClient.builder(httpHost).setHttpClientConfigCallback(this::setHttpClientConfigCallback);

        return new RestHighLevelClient(builder);
    }

    private HttpAsyncClientBuilder setHttpClientConfigCallback(HttpAsyncClientBuilder builder) {
        builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

        // TODO authentication
//        if (configuration.authentication.isPresent()) {
//            setupBasicAuthentication(builder);
//        }

        return builder;
    }

//    private void setupBasicAuthentication(HttpAsyncClientBuilder builder) {
//        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
//        credentialsProvider.setCredentials(
//                AuthScope.ANY,
//                new UsernamePasswordCredentials(
//                        configuration.authentication.username, configuration.authentication.password));
//
//        builder.setDefaultCredentialsProvider(credentialsProvider);
//    }

    private static HttpHost urlToHttpHost(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ElasticsearchExporterException("Failed to parse url " + url, e);
        }

        return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    }

    protected String indexFor(JSONObject record) {
        Instant timestamp = Instant.ofEpochMilli(record.getLong("timestamp"));
        return indexPrefixForValueTypeWithDelimiter(ExtendedValueType.valueOf(record.getString("valueType"))) + formatter.format(timestamp);
    }

    protected String idFor(JSONObject record) {
        return record.getInt("partitionId") + "-" + record.getLong("position");
    }

    protected String typeFor(JSONObject record) {
        return "_doc";
    }

    protected String indexPrefixForValueTypeWithDelimiter(ExtendedValueType extendedValueType) {
        return indexPrefixForValueType(extendedValueType) + INDEX_DELIMITER;
    }

    private String aliasNameForValueType(ExtendedValueType extendedValueType) {
        return indexPrefix + ALIAS_DELIMITER + valueTypeToString(extendedValueType);
    }

    private String indexPrefixForValueType(ExtendedValueType valueType) {
        String version = VersionUtil.getVersionLowerCase();

        return indexPrefix
                + INDEX_DELIMITER
                + valueTypeToString(valueType)
                + INDEX_DELIMITER
                + version;
    }

    private static String valueTypeToString(ExtendedValueType extendedValueType) {
        if(extendedValueType.name().equalsIgnoreCase("process_instance")){
            extendedValueType = ExtendedValueType.WORKFLOW_INSTANCE;
        }
        return extendedValueType.name().toLowerCase().replaceAll("_", "-");
    }

    private static String indexTemplateForValueType(ExtendedValueType valueType) {
        return String.format(INDEX_TEMPLATE_FILENAME_PATTERN, valueTypeToString(valueType));
    }
}
