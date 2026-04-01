package victor.training.performance.leak;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequiredArgsConstructor
public class Leak24_MetricTags {
  private final MeterRegistry meterRegistry;
  private final RestTemplate restTemplate;

  @GetMapping("leak24/{uuid}")
  public String endpoint(@PathVariable String uuid) {
    String url = "http://localhost:8080/actuator/health?id=" + uuid;

    meterRegistry.timer("sas", "notificationId", uuid)
        .record(() -> restTemplate.getForEntity(url, String.class));

    return "Try other ids and check the metrics http_client_requests_seconds_* at http://localhost:8080/actuator/prometheus";
  }
}
/**
 * KEY-POINTS
 * ⭐️ High-cardinality tags = unbounded metrics expansion
 * ⭐️ Outbound REST calls were auto-tagged with their URL in Spring Boot 2 😱
 * Details: https://stackoverflow.com/questions/74962369/uri-tag-of-http-client-requests-metric-as-none-in-spring-boot-3-0-x
 * 🙏 Thanks to Vladyslav D at Devoxx BE 2025
 *
 */