package victor.training.performance.leak;

import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import victor.training.performance.leak.obj.Big20MB;

import java.util.concurrent.Semaphore;

import static victor.training.performance.util.PerformanceUtil.done;
import static victor.training.performance.util.PerformanceUtil.getUsedHeapHuman;

@RestController
public class Leak5_ThreadLocal {
  private static final ThreadLocal<RequestContext> threadLocal = new ThreadLocal<>();

  record RequestContext(String currentUser, Big20MB big) {
  }

  Semaphore semaphore = new Semaphore(10);
//  @Bulkhead// resilience4
  @GetMapping("leak5")
  public String controller() throws InterruptedException {
    String currentUsername = "john.doe"; // from request header/JWT/http session
    semaphore.acquire();
    threadLocal.set(new RequestContext(currentUsername, new Big20MB()));
    try { // RULE: immediately after .set do try {..}finally{.remove}
      //BETTER: AVOID USING YOUR OWN THREAD LOCALS
      // a) prefer to attach to SecurityHolderHolder principal
      // b) open telemetry baggage / MDC.put/clear for debugging info

      // restrict this method to ≤10 in parallel
      service();
    } finally {
      semaphore.release();
      threadLocal.remove();
    }
    return "Magic can hurt " + done() + "<p>" + getUsedHeapHuman();
  }

  private void service() {
//    if (true) throw new RuntimeException("BUG🐞");
    repo();
  }

  private void repo() {
    String currentUsername = threadLocal.get().currentUser();
    System.out.println("UPDATE ... MODIFIED_BY=" + currentUsername);
  }
}

/** ⭐️ KEY POINTS
 * 🧠 ThreadLocal is used in BE to propagate invisible 'metadata':
 *   - Security Principal ± Rights
 *   - Observability: Logback MDC / Trace ID / OTEL Baggage
 *   - @Transactional/JDBC Connection ± Hibernate Session
 * 👍 Keep it small
 * ☢️ TL might remain attached to idle worker thread in a pool ~> Memory Leak
 * ☢️ TL might leak to the next task of the same worker
 * ☢️ TL might not propagate from submitter thread to worker thread(s)
 * 👍 Prefer framework-managed ThreadLocal data over creating your own: MDC, Baggage, SecurityContextHolder
 * 👍 On your own ThreadLocal: #set(..); try { <work> } finally {#remove();}
 */


