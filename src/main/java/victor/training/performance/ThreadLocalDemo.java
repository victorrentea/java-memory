package victor.training.performance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.springframework.http.HttpMethod.GET;
import static victor.training.performance.util.PerformanceUtil.sleepMillis;

@Slf4j
@Component
public class ThreadLocalDemo {
  public static void main(String[] args) {
    MyController controller = new MyController(new MyService(new MyRepo()));
    CompletableFuture.runAsync(() -> simulateRequest("alice", "alice's data", controller));
    CompletableFuture.runAsync(() -> simulateRequest("bob", "bob's data", controller));
    sleepMillis(100);
  }

  public static void simulateRequest(String user, String data, MyController controller) {
    log.info("Current user: {}", user); // from header/session/JWT...
    Holder.currentUser.set(user);
    controller.create(data);
  }
}

class Holder {
  public static ThreadLocal<String> currentUser = new ThreadLocal<>() ;
}

@RestController
@RequiredArgsConstructor
class MyController {
  private final MyService service;

  @GetMapping("thread-locals")
  public void create(@RequestParam String data) {
    service.create(data);
  }
}

@Service
@RequiredArgsConstructor
class MyService {
  private final MyRepo repo;

  public void create(String data) {
    sleepMillis(10); // to race
    repo.save(data);
  }
}

@Repository
@Slf4j
class MyRepo {
  public void save(String data) {
    String user = Holder.currentUser.get();
    log.info("INSERT INTO A (data={}, last_modified_by={}) ", data, user);
  }
}

// @Configuration
class ThreadPoolConfig {
  // region executorPropagatingThreadLocal TODO
  @Bean
  public ThreadPoolTaskExecutor executorPropagatingThreadLocal() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setTaskDecorator(runnable -> { // runs in submitter thread
//      String tenantId = Holder.currentUser.get();
      return () -> { // runs in worker thread
//        Holder.currentUser.set(tenantId);
        try {
          runnable.run();
        } finally {
//          Holder.currentUser.remove();
        }
      };
    });
    executor.initialize();
    return executor;
  }
  // endregion

  // region HTTP filter extracting username from request header TODO
  @Bean
  public HttpFilter tenantFilter() {
    return new HttpFilter() {
      @Override
      protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String tenantId = request.getHeader("x-user");
//        Holder.currentUser.set(tenantId);
        try {
          super.doFilter(request, response, chain);
        } finally {
//          Holder.currentUser.remove();
        }
        // TODO similar for message listeners, SOAP endpoints, ..., and schedulers
      }
    };
  }
  //endregion

  // region demo calling http to spring app
  @EventListener(ApplicationStartedEvent.class)
  private void demo() {
    CompletableFuture.runAsync(() -> httpRequest("alice", "alice's data"));
    CompletableFuture.runAsync(() -> httpRequest("bob", "bob's data"));
  }

  public void httpRequest(String user, String data) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("x-user", user);
    RestTemplate rest = new RestTemplate();
    rest.exchange("http://localhost:8080/thread-locals?data=" + data,
        GET,
        new HttpEntity<>(headers),
        Void.class);
  }
  //endregion
}
