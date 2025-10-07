package victor.training.performance.leak;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class IdempotencyFilter extends HttpFilter {
  private final Set<Call> previousCalls = Collections.synchronizedSet(new HashSet<>());

  record Call(String idempotencyKey, long ts) {}

  @Scheduled(fixedRate = 1000)
  public void clearOldCalls() {
    log.info("Clearing old calls. Current size: {}", previousCalls.size());
    long threshold = System.currentTimeMillis() - 3000;
    previousCalls.removeIf(call -> call.ts < threshold);
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    if (request.getRequestURI().endsWith("leak11")) {
      String idempotencyKey = request.getHeader("Idempotency-Key");
      if (idempotencyKey == null) {
        reject(response, "Missing request header 'Idempotency-Key'");
        return;
      }
//      boolean newIK = previousCalls.add(new Call(idempotencyKey, System.currentTimeMillis()));
      boolean newIK = previousCalls.stream().noneMatch(call -> call.idempotencyKey.equals(idempotencyKey));
      previousCalls.add(new Call(idempotencyKey, System.currentTimeMillis()));
      if (!newIK) {
        reject(response, "Duplicate call rejected!");
        return;
      }
    }

    chain.doFilter(request, response);
  }

  private void reject(HttpServletResponse response, String message) throws IOException {
    response.setStatus(400);
    response.setContentType("text/plain");
    response.getWriter().write(message);
  }
}

/**
 * ⭐️ KEY POINTS
 * ☣️ Most leaks occur in libraries or unknown code
 * ☣️ Set a max size to any permanent collection: List, Set, Map<♾️keys
 * 👍 For a cache: use a library with key max-count/ttl
 */

