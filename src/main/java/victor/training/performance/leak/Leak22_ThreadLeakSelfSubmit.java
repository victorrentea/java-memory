package victor.training.performance.leak;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@RestController
@RequestMapping("leak22")
@RequiredArgsConstructor
public class Leak22_ThreadLeakSelfSubmit {
  private final Confused confused;
  @GetMapping
  public String hotEndpoint() throws InterruptedException, ExecutionException {
    CompletableFuture<String> f1 = confused.async();
    CompletableFuture<String> f2 = confused.async();
    return f1.get() + f2.get();
  }
}
@Service
@RequiredArgsConstructor
class Confused {
  private final ThreadPoolTaskExecutor myExecutor;

  @Async("myExecutor") //10 threaduri
  public CompletableFuture<String> async() {
    return CompletableFuture.supplyAsync(()->networkCall(),myExecutor);
  }

  @SneakyThrows
  public static String networkCall() {
    Thread.sleep(1000);
    return "data";
  }
}
@Configuration
@EnableAsync
class Config {
  @Bean
  ThreadPoolTaskExecutor myExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.initialize();
    return executor;
  }
}
