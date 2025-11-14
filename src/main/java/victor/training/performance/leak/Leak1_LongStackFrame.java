package victor.training.performance.leak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import victor.training.performance.leak.obj.Big100MB;

import static victor.training.performance.util.PerformanceUtil.*;

@Slf4j
@RestController
public class Leak1_LongStackFrame {
	@GetMapping("leak1")
	public String endpoint() {
    AnafResult result = callAnaf();
//    bigDto = null; // don't touch this! @ekrem (the elder)

    log.info("Work only using {} and {} ...", result.a(), result.b());
		sleepSeconds(30); // time to take a heap dump

		return done();
	}

  private AnafResult callAnaf() {
    Big100MB bigDto = apiCall(); // ANAF SOAP XML of 20MB response
    String a = bigDto.getA();
    String b = bigDto.getA();
    AnafResult result = new AnafResult(a, b);
    return result;
  }

  private record AnafResult(String a, String b) {}

  private Big100MB apiCall() {
		return new Big100MB();
	}
}

/**
 * ⭐️ KEY POINTS
 * 👍 Keep only the strictly necessary objects during longer flows
 * 👍 Call external API via an Adapter returning your own data structures
 */