package org.mifos.phee.kafkastreamer.importer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class KafkaElasticImportApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaElasticImportApplication.class, args);
	}

}
