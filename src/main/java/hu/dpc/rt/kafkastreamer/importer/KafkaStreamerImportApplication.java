package hu.dpc.rt.kafkastreamer.importer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class KafkaStreamerImportApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaStreamerImportApplication.class, args);
	}

}
