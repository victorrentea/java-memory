package victor.training.performance.leak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

   // ❌ BAD: creating a new HttpClient per request
   @GetMapping
   public String leaky() throws Exception {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();
      // Should be: try (HttpClient client = HttpClient.newBuilder()...build()) {

      HttpResponse<String> response = client.send(
          HttpRequest.newBuilder(URI.create("https://httpbin.org/get")).build(),
          HttpResponse.BodyHandlers.ofString()
      );
      // client.close() is never called!
      // Client's thread pool + selector + connections stay alive until GC

      int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
      log.info("Response: {} bytes, total JVM threads: {}",
          response.body().length(), threadCount);
      return "OK, threads=" + threadCount;
   }

   // ✅ GOOD: reuse a single HttpClient (singleton)
   // private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
   //     .connectTimeout(Duration.ofSeconds(5))
   //     .build();
   //
   // @GetMapping("/fixed")
   // public String fixed() throws Exception {
   //     HttpResponse<String> response = SHARED_CLIENT.send(
   //         HttpRequest.newBuilder(URI.create("https://httpbin.org/get")).build(),
   //         HttpResponse.BodyHandlers.ofString());
   //     return "OK, threads=" + ManagementFactory.getThreadMXBean().getThreadCount();
   // }
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
