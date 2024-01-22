package org.mifos.phee.kafkastreamer.importer;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONObject;
import org.opensearch.OpenSearchException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.PutIndexTemplateRequest;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
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

    @Value("${importer.elasticsearch.url}")
    private String elasticUrl;

    @Value("${importer.elasticsearch.index-prefix}")
    private String indexPrefix;

    @Value("${reporting.enabled}")
    private Boolean reportingEnabled;

    @Value("${elasticsearch.security.enabled}")
    private Boolean securityEnabled;

    @Value("${elasticsearch.sslVerification}")
    private Boolean sslVerify;

    @Value("${elasticsearch.username}")
    private String username;

    @Value("${elasticsearch.password}")
    private String password;

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private PaymentsIndexConfiguration paymentsIndexConfiguration;

    @Autowired
    private BulkRequestCollector bulkRequestCollector;

    @Autowired
    private JsonCleaner jsonCleaner;

    private RestHighLevelClient client;
//    private ElasticsearchMetrics metrics;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    @PostConstruct
    public void setup() {
        this.client = createClient();
        logger.info("configured to using Elasticseach at: {}", elasticUrl);
        taskScheduler.schedule(() -> bulkRequestCollector.flush(client), new PeriodicTrigger(2000));
    }

    public void close() throws IOException {
        client.close();
    }

    public void bulk(UpdateRequest updateRequest) {
        logger.trace("Calling bulk request for upsert");
        bulkRequestCollector.add(updateRequest);
    }

    public void index(JSONObject record) {
//        if (metrics == null) {
//            metrics = new ElasticsearchMetrics(record.getInt("partitionId"));
//        }
        if (reportingEnabled) {
            logger.trace("Getting index method called with record value type {}", record.getString("valueType"));
            upsertToReportingIndex(record);
        }
//        logger.trace("Pushing index for " + indexFor(record));
//        IndexRequest request = new IndexRequest(indexFor(record), typeFor(record), idFor(record))

        String source = jsonCleaner.sanitize(record).toString();

        IndexRequest request = new IndexRequest(indexFor(record))
                .source(source, XContentType.JSON)
                .routing(Integer.toString(record.getInt("partitionId")));
        logger.trace("Calling bulk request for insert");
        bulkRequestCollector.add(request);

        if (bulkRequestCollector.shouldFlush()) {
            int flushed = bulkRequestCollector.flush(client);
            logger.info("flushed {} records to ES", flushed);
        }
    }


    public void upsertToReportingIndex(JSONObject record) {
        JSONObject newRecord = new JSONObject();
        if (record.getString("valueType").equalsIgnoreCase("variable")) {
            JSONObject valueObj = record.getJSONObject("value");
            if (valueObj.has("name")) {
                if (paymentsIndexConfiguration.getVariables().contains(valueObj.getString("name"))) {
                    if (valueObj.getString("name").equalsIgnoreCase("amount")) {
                        newRecord.put((String) valueObj.get("name"),
                                Double.parseDouble(valueObj.getString("value").replaceAll("\"",
                                        "")));
                    } else if (valueObj.getString("name").equalsIgnoreCase("originDate")) {
                        Instant timestamp = Instant.ofEpochMilli(valueObj.getLong("value"));
                        newRecord.put((String) valueObj.get("name"), timestamp);
                    } else {
                        newRecord.put((String) valueObj.get("name"), valueObj.get("value").toString()
                                .replaceAll("\"", ""));
                    }
                }
                if (!newRecord.has("processInstanceKey"))
                    newRecord.put("processInstanceKey",
                            String.valueOf(valueObj.getLong("processInstanceKey")));
                Instant timestamp = Instant.ofEpochMilli(record.getLong("timestamp"));
                newRecord.put("timestamp", timestamp);
            }
            logger.trace("New Record before insert is: " + newRecord);
            String version = getVersion();
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

    private static String getVersion() {
        return VersionUtil.getVersion();
    }


    /**
     * @return true if request was acknowledged
     */
    public boolean putIndexTemplate(ValueType ValueType) {
        String templateName = indexPrefixForValueType(ValueType);
        String aliasName = aliasNameForValueType(ValueType);
        String filename = indexTemplateForValueType(ValueType);
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
                throw new RuntimeException("Failed to find index template in classpath " + filename);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load index template from classpath " + filename, e);
        }

        // update prefix in template in case it was changed in configuration
        template.put("index_patterns", Collections.singletonList(templateName + INDEX_DELIMITER + "*"));

        // update alias in template in case it was changed in configuration
        template.put("aliases", Collections.singletonMap(aliasName, Collections.EMPTY_MAP));

        if (reportingEnabled) {
            if (templateName.equals("zeebe-record")) {
                Map<String, Object> template1;
                try (InputStream inputStream1 = KafkaElasticImportApplication.class.getResourceAsStream("/zeebe-payments.json")) {
                    if (inputStream1 != null) {
                        template1 = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream1, true);
                    } else {
                        throw new RuntimeException("Failed to find index template in classpath " + filename);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load index template from classpath " + filename, e);
                }
                template1.put("index_patterns", Collections.singletonList("zeebe-payments" + INDEX_DELIMITER + "*"));
                template1.put("aliases", Collections.singletonMap("zeebe-payments", Collections.EMPTY_MAP));
                PutIndexTemplateRequest request = new PutIndexTemplateRequest("zeebe-payments").source(template1);
                putIndexTemplate(request);
            }
        }
        PutIndexTemplateRequest request = new PutIndexTemplateRequest(templateName).source(template);

        return putIndexTemplate(request);
    }

    /**
     * @return true if request was acknowledged
     */
    private boolean putIndexTemplate(org.opensearch.client.indices.PutIndexTemplateRequest putIndexTemplateRequest) {
        try {
            return client
                    .indices()
                    .putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT)
                    .isAcknowledged();
        } catch (OpenSearchException exception) {
            throw new RuntimeException("Failed to Connect ES", exception);
        } catch (IOException e) {
            throw new RuntimeException("Failed to put index template", e);
        }
    }

    private RestHighLevelClient createClient() {
        org.opensearch.client.RestClientBuilder builder;
        SSLContext sslContext = null;
        if (securityEnabled) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            if (sslVerify) {
                SSLContextBuilder sslBuilder;
                try {
                    sslBuilder = SSLContexts.custom().loadTrustMaterial(null, (x509Certificates, s) -> true);
                    sslContext = sslBuilder.build();
                } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
                    throw new RuntimeException(e);
                }
                HttpHost httpHost = urlToHttpHost(elasticUrl);
                SSLContext finalSslContext = sslContext;
                builder = RestClient.builder(httpHost)
                        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                                .setSSLContext(finalSslContext)
                                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .setDefaultCredentialsProvider(credentialsProvider));
            } else {
                HttpHost httpHost = urlToHttpHost(elasticUrl);
                builder = RestClient.builder(httpHost)
                        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider));
            }
        } else {
            HttpHost httpHost = urlToHttpHost(elasticUrl);
            builder =
                    org.opensearch.client.RestClient.builder(httpHost).setHttpClientConfigCallback(this::setHttpClientConfigCallback);
        }
        return new RestHighLevelClient(builder);
    }

    private HttpAsyncClientBuilder setHttpClientConfigCallback(HttpAsyncClientBuilder builder) {
        builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

        return builder;
    }

    private static HttpHost urlToHttpHost(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to parse url " + url, e);
        }

        return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    }

    protected String indexFor(JSONObject record) {
        Instant timestamp = Instant.ofEpochMilli(record.getLong("timestamp"));
        return indexPrefixForValueTypeWithDelimiter(ValueType.valueOf(record.getString("valueType"))) + formatter.format(timestamp);
    }

    protected String idFor(JSONObject record) {
        return record.getInt("partitionId") + "-" + record.getLong("position");
    }

    protected String typeFor(JSONObject record) {
        return "_doc";
    }

    protected String indexPrefixForValueTypeWithDelimiter(ValueType ValueType) {
        return indexPrefixForValueType(ValueType) + INDEX_DELIMITER;
    }

    private String aliasNameForValueType(ValueType ValueType) {
        return indexPrefix + ALIAS_DELIMITER + valueTypeToString(ValueType);
    }

    private String indexPrefixForValueType(ValueType valueType) {
        String version = getVersion();

        return indexPrefix
                + INDEX_DELIMITER
                + valueTypeToString(valueType)
                + INDEX_DELIMITER
                + version;
    }

    private static String valueTypeToString(ValueType ValueType) {
        return ValueType.name().toLowerCase().replaceAll("_", "-");
    }

    private static String indexTemplateForValueType(ValueType valueType) {
        return String.format(INDEX_TEMPLATE_FILENAME_PATTERN, valueTypeToString(valueType));
    }
}
