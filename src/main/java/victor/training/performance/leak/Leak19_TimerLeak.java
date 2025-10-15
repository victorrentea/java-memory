package victor.training.performance.leak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import victor.training.performance.leak.obj.Big1KB;

import java.io.IOException;
import java.time.LocalTime;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static victor.training.performance.util.PerformanceUtil.sleepMillis;

@SuppressWarnings("resource")
@Slf4j
@RestController
@RequiredArgsConstructor
public class Leak19_TimerLeak {
  private static final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

  @GetMapping(value = "leak19", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamSseMvc() {
    SseEmitter emitter = new SseEmitter();
    scheduler.scheduleAtFixedRate(() -> {
      var someData = new Big1KB();
      for (int i = 0; true; i++) {
        log.info("Tick "+i);
        SseEventBuilder event = SseEmitter.event()
            .data("SSE@" + LocalTime.now())
            .id(String.valueOf(i));
        try {
          emitter.send(event);
        } catch (Exception e) { // TO-DO what?
        }
        sleepMillis(500);
      }
    }, 0, 500, TimeUnit.MILLISECONDS);
    return emitter;
  }
}
