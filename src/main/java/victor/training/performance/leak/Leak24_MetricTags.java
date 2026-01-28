package victor.training.performance.leak;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SuppressWarnings("resource")
@Slf4j
@RestController
@RequiredArgsConstructor
public class Leak24_MetricTags {
  private final MeterRegistry meterRegistry;
  private final RestTemplate restTemplate;

  @GetMapping("leak24/{notificationId}")
  public String endpoint(@PathVariable String notificationId) {
    // on Spring Boot < 3, external calls metrics are tagged with their URI, here=unbound => OOME❌
    String uri = "http://example.com/notification/" + notificationId;
    restTemplate.getForEntity(uri, String.class);

    // Custom tag has high cardinality => potential OOME❌
    // meterRegistry.timer("sas", "notificationId", notificationId)
    meterRegistry.timer("sas")
      .record(() -> "simulated work");
    return "Try other ids and check the metrics http_client_requests_seconds_* at http://localhost:8080/actuator/prometheus";
  }
}
// thanks to: Vladyslav D., after my Devoxx BE 2025
// see https://stackoverflow.com/questions/74962369/uri-tag-of-http-client-requests-metric-as-none-in-spring-boot-3-0-x