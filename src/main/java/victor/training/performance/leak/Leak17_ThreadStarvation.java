package victor.training.performance.leak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.concurrent.Semaphore;

import static victor.training.performance.util.PerformanceUtil.sleepMillis;

@Slf4j
@RestController
@RequestMapping("leak17")
public class Leak17_ThreadStarvation {
  private final Semaphore semaphore = new Semaphore(200-1); // 1 for liveness

  @GetMapping // call it 500 times to saturate Tomcat's thread pool: 200 in action + 300 in queue
  public /*Flux<*/String hotEndpoint() throws InterruptedException {
    // FIXME semaphore.acquire()/.release()

    return slow(); // tensorflow/LLMish/AIish runs on GPU for 20s
  }

  @GetMapping("/liveness")
  public String liveness() {
    return "k8s, 🙏 please don't kill me! Responded at " + LocalDateTime.now();
    // Handling each incoming HTTP request on a new Virtual Threads would fix this problem
  }

  private String slow() {
    sleepMillis(20_000); // pretend CPU/GPU/SQL/API call
    return "result";
  }
}
