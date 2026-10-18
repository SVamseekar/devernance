package os.assurance.eu.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@org.springframework.boot.context.properties.EnableConfigurationProperties(
    os.assurance.eu.api.evidence.FileStorageProperties.class)
public class DevernanceApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(DevernanceApiApplication.class, args);
  }
}
