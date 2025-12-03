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

  @GetMapping("leak24/{id}")
  public String endpoint(@PathVariable String id) {
    apiCall(id);
//    meterRegistry.timer("sas", "uri", id).record(() -> {apiCall(id);}); // ❌ DIY leaks
    return "Try other ids and check the metrics http_client_requests_seconds_* at http://localhost:8080/actuator/prometheus";
  }

  private void apiCall(String id) {
    String uri = "http://example.com/product" + id;
    // ✅ tag uri=NONE on SBP ≥ 3.0.x
    restTemplate.getForEntity(uri, String.class);
  }
}
// thanks to: Vladyslav D.
// https://stackoverflow.com/questions/74962369/uri-tag-of-http-client-requests-metric-as-none-in-spring-boot-3-0-x