# ph-ee-importer-es
## Purpose
PHEE's Elasticsearch importer project helps to import Zeebe Events from Kafka to Elasticsearch.

## Configuration
The following options are configurable for various environments from the environment-specific kubernetes configmap:

```yaml
kafka:
  brokers: "kafka:9092"
  msk: false

importer:
  elasticsearch:
    url: "http://ph-ee-elasticsearch:9200/"


elasticsearch:
  security:
    enabled: false
  sslVerification: false
  username: "elastic"
  password: "somepassword"
```

It's also possible to override these values using environment variables, eg. `KAFKA_BROKERS` or `ELASTICSEARCH_USERNAME`.

