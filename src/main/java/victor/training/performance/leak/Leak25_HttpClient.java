package victor.training.performance.leak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static victor.training.performance.util.PerformanceUtil.*;

/**
 * java.net.http.HttpClient creates its own thread pool + NIO selector per instance.
 * Creating a new HttpClient per request leaks threads and native resources.
 *
 * Before Java 21: HttpClient had NO close() method — threads leaked until GC.
 * Java 21+: HttpClient implements AutoCloseable, but you must actually call close().
 *
 * Under sustained load, new HttpClients are created faster than GC reclaims old ones.
 * Each client holds 2-3 daemon threads (~256KB stack each) + NIO selector + connection pool.
 * 100 concurrent requests = 300 leaked threads = ~75MB of stack memory alone.
 */
@Slf4j
@RestController
@RequestMapping("leak25")
public class Leak25_HttpClient {
   @GetMapping
   public String leaky() throws Exception {
      var httpClient = HttpClient.newBuilder().build(); // ❌
      var response = httpClient.send(
          HttpRequest.newBuilder(URI.create("http://localhost:8080/actuator/health")).build(),
          ofString());
      return ("OK got %d bytes | threads: %d | heap: %s | RSS: %s")
          .formatted(response.body().length(), getThreadCount(), getUsedHeapHuman(), getProcessMemoryHuman());
   }
      // Client's thread pool + selector + connections stay alive until GC
}

/**
 * ⭐️ KEY POINTS
 * - HttpClient creates its own thread pool (2-3 threads) + NIO selector per instance
 * - Before Java 21: no close() method at all — could only wait for GC to reclaim
 * - Java 21+: implements AutoCloseable, but forgetting close() still leaks
 * - Under load, leaked threads accumulate faster than GC can reclaim them
 * - Fix: reuse a singleton HttpClient (inject as a Spring @Bean)
 * - Fix: if per-request is needed, use try-with-resources: try (var client = HttpClient.newBuilder()...build())
 * - Each leaked client costs ~768KB (3 threads × 256KB stack) + connection buffers
 * - 100 leaked clients = ~75MB of thread stacks alone (off-heap, invisible in heap dump!)
 */
