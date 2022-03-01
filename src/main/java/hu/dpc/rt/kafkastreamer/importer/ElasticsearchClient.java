package hu.dpc.rt.kafkastreamer.importer;

import io.prometheus.client.Histogram;
import io.zeebe.exporter.ElasticsearchExporter;
import io.zeebe.exporter.ElasticsearchExporterException;
import io.zeebe.exporter.ElasticsearchMetrics;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.util.VersionUtil;
import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
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
import org.springframework.scheduling.annotation.Scheduled;
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
        bulkRequest.add(indexRequest);
    }

    public void index(JSONObject record) {
        if (metrics == null) {
            metrics = new ElasticsearchMetrics(record.getInt("partitionId"));
        }
        if(record.has("value")){
            JSONObject valueObj = record.getJSONObject("value");
            if(valueObj.has("processInstanceKey")) {
                Long processId = valueObj.getLong("processInstanceKey");
                logger.info("Value Obj before :" + valueObj);
                valueObj.put("processInstanceKey", String.valueOf(processId));
                logger.info("Value Obj After :" + valueObj);
                record.put("value", valueObj);
            }
        }
        IndexRequest request =
                new IndexRequest(indexFor(record), typeFor(record), idFor(record))
                        .source(record.toString(), XContentType.JSON)
                        .routing(Integer.toString(record.getInt("partitionId")));
        bulk(request);
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
    public boolean putIndexTemplate(ValueType valueType) {
        String templateName = indexPrefixForValueType(valueType);
        String aliasName = aliasNameForValueType(valueType);
        String filename = indexTemplateForValueType(valueType);
        return putIndexTemplate(templateName, aliasName, filename);
    }

    /**
     * @return true if request was acknowledged
     */
    public boolean putIndexTemplate(
            String templateName, String aliasName, String filename) {
        Map<String, Object> template;
        try (InputStream inputStream =
                     ElasticsearchExporter.class.getResourceAsStream(filename)) {
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
        return indexPrefixForValueTypeWithDelimiter(ValueType.valueOf(record.getString("valueType"))) + formatter.format(timestamp);
    }

    protected String idFor(JSONObject record) {
        return record.getInt("partitionId") + "-" + record.getLong("position");
    }

    protected String typeFor(JSONObject record) {
        return "_doc";
    }

    protected String indexPrefixForValueTypeWithDelimiter(ValueType valueType) {
        return indexPrefixForValueType(valueType) + INDEX_DELIMITER;
    }

    private String aliasNameForValueType(ValueType valueType) {
        return indexPrefix + ALIAS_DELIMITER + valueTypeToString(valueType);
    }

    private String indexPrefixForValueType(ValueType valueType) {
        String version = VersionUtil.getVersionLowerCase();
        return indexPrefix
                + INDEX_DELIMITER
                + valueTypeToString(valueType)
                + INDEX_DELIMITER
                + version;
    }

    private static String valueTypeToString(ValueType valueType) {
        return valueType.name().toLowerCase().replaceAll("_", "-");
    }

    private static String indexTemplateForValueType(ValueType valueType) {
        return String.format(INDEX_TEMPLATE_FILENAME_PATTERN, valueTypeToString(valueType));
    }
}
