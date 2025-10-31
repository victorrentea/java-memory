package victor.training.performance.leak;

import lombok.RequiredArgsConstructor;
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

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static victor.training.performance.util.PerformanceUtil.sleepMillis;

@Slf4j
@RestController
@RequestMapping("leak22")
@RequiredArgsConstructor
public class Leak22_ThreadPoolSelfSubmit {
  private final Confused confused;
  @GetMapping
  public String deadlock() throws InterruptedException, ExecutionException {
    return confused.asyncDeadlock().get(); // imagine 3 x
  }
}
@Service
@RequiredArgsConstructor
@Slf4j
class Confused {
  private final ThreadPoolTaskExecutor executor3; // has 3 threads

  @Async("executor3")
  public CompletableFuture<String> asyncDeadlock() throws ExecutionException, InterruptedException {
    sleepMillis(100); // calls can overlap
    CompletableFuture<String> f1 = supplyAsync(()-> apiCall(), executor3);
    CompletableFuture<String> f2 = supplyAsync(()-> apiCall(), executor3);
    return CompletableFuture.completedFuture(f1.get() + f2.get());
  }

  public static String apiCall() {
    return "remote data";
  }
}
@Configuration
@EnableAsync
class Config {
  @Bean
  ThreadPoolTaskExecutor executor3() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setMaxPoolSize(3);
    executor.setQueueCapacity(100);
    executor.initialize();
    return executor;
  }
}
