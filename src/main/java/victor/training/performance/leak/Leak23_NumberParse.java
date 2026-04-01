package victor.training.performance.leak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static java.util.concurrent.CompletableFuture.supplyAsync;

@Slf4j
@RestController
@RequestMapping("leak23")
@RequiredArgsConstructor
public class Leak23_NumberParse {
  @GetMapping
  // ✅ expected http://localhost:8080/leak23?number=500.2
  // ✅ expected http://localhost:8080/leak23?number=1E10
  // ❌ boom: http://localhost:8080/leak23?number=1E10&uuid=51635621364e981261465&
  public String parse(@RequestParam Map<String, String> json) throws InterruptedException, ExecutionException {
    log.info("Raw JSON: {}", json);
    autoParse(json);
    log.info("Auto-parsed JSON: {}", json);
    return "OK✅";
  }

  private static final Pattern SCIENCE_NUMBER = Pattern.compile("[-+]?\\d*\\.?\\d+[eE][-+]?\\d+");
  private static void autoParse(Map<String, String> json) {
    for (String key : json.keySet()) {
      String valueString = json.get(key);
      if (SCIENCE_NUMBER.matcher(valueString).matches()) {
        json.put(key, new BigDecimal(valueString).toPlainString());
      }
    }
  }
}

// Credits: @oleg