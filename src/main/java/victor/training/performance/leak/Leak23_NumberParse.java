package victor.training.performance.leak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static victor.training.performance.util.PerformanceUtil.sleepMillis;

@Slf4j
@RestController
@RequestMapping("leak23")
@RequiredArgsConstructor
public class Leak23_NumberParse {
  @GetMapping
  public void parse(@RequestParam Map<String, Object> json) throws InterruptedException, ExecutionException {
    log.info("Got: {}", json);
    SmartParser.autoParse(json); //@oleg
  }
}
class SmartParser {
  private static final Pattern SCIENCE_NUMBER = Pattern.compile("[-+]?\\d*\\.?\\d+[eE][-+]?\\d+");
  public static void autoParse(Map<String, Object> json) {
    for (String key : json.keySet()) {
      String valueString = json.get(key).toString();
      if (SCIENCE_NUMBER.matcher(valueString).matches()) {
        json.put(key, new BigDecimal(valueString));
      }
    }
  }
}