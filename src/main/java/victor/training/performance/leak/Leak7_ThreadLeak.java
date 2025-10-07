package victor.training.performance.leak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static victor.training.performance.util.PerformanceUtil.*;

@SuppressWarnings("resource")
@Slf4j
@RestController
@RequiredArgsConstructor
public class Leak7_ThreadLeak {
  private final ThreadPoolTaskExecutor myExecutor;

  @GetMapping("leak7")
  public String endpoint() throws ExecutionException, InterruptedException {
//    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    var f1 = CompletableFuture.supplyAsync(Leak7_ThreadLeak::apiCallA,myExecutor);
    var f2 = CompletableFuture.supplyAsync(Leak7_ThreadLeak::apiCallB,myExecutor);
    return f1.get() + f2.get() + done() + "<p>" + getUsedHeapHuman() + "<p>" + getProcessMemoryHuman();
  }

  private static String apiCallA() {
    sleepMillis(100);
    return "A";
  }

  private static String apiCallB() {
    sleepMillis(100);
    return "B";
  }
}

/** ⭐️ KEY POINTS
 * ☣️ Fixed thread pool not #shutdown() keeps worker threads alive forever
 * 👍 try-with-resource your thread pools
 * 👍👍 Better: inject and use a singleton Spring-managed ThreadPoolTaskExecutor
 */
