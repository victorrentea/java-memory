package victor.training.performance.leak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import victor.training.performance.leak.Adapter.MyVO;
import victor.training.performance.leak.obj.Big100MB;

import static victor.training.performance.util.PerformanceUtil.*;

@Slf4j
@RestController
public class Leak1_LongStackFrame {
  private final Adapter adapter;

  public Leak1_LongStackFrame(Adapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("leak1")
  public String endpoint() {
    MyVO result = adapter.fetch();

    log.info("Work only using {} and {} ...", result.a(), result.b());
    sleepSeconds(30); // time to take a heap dump

    return done();
  }
}

@Slf4j
@RequiredArgsConstructor
@Service
class Adapter {
  public MyVO fetch() {
    Big100MB bigDto = apiCall();
    String a = bigDto.getA();
    String b = bigDto.getA();
    return new MyVO(a, b);
  }

  public record MyVO(String a, String b) {}

  private Big100MB apiCall() {
		return new Big100MB();
	}
}

/**
 * ⭐️ KEY POINTS
 * 👍 Keep only the strictly necessary objects during longer flows
 * 👍 Call external API via an Adapter returning your own data structures
 */