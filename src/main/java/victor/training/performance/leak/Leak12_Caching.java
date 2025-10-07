package victor.training.performance.leak;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import victor.training.performance.leak.CacheService.InquiryParams;
import victor.training.performance.leak.obj.Big20MB;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.synchronizedMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static victor.training.performance.util.PerformanceUtil.done;
import static victor.training.performance.util.PerformanceUtil.getUsedHeapHuman;

@Service
@Slf4j
@RequiredArgsConstructor
class CacheService {
  private Big20MB fetchData() {
    return new Big20MB();
  }

  // === ❌ Anti-Pattern: Manual Cache ===
  Map<LocalDate, Big20MB> fexCache = synchronizedMap(new HashMap<>());
  // My wheel is the best wheel 🛞
  public Big20MB getTodayFex(LocalDate date) {
    return fexCache.computeIfAbsent(date, d -> {
      log.debug("Fetch data for date: {}", date);
      return fetchData();
    });
  }

  @Bean
  MeterBinder fexCacheMetrics() {// 🔔ALARM on this
    return registry -> Gauge.builder("fex_cache_size", fexCache, Map::size).register(registry);
  }

  // === ❌ Cache Key Mess-up #1 ===
  @Cacheable("signature") // a proxy returns the previous value for the same parameter(s)
  // last commit: added request time 💪 - @vibe_coder😎
  public Big20MB fetchById(Long id, long requestTime) {
    log.debug("Fetch contract id={} at {}", id, requestTime);
    return fetchData();
  }

  // === ❌ Cache Key Mess-up #2 ===
  @RequiredArgsConstructor
  @Getter
  @Setter
  static class InquiryParams {
    private final UUID contractId;
    private final int year;
    private final int month;
  }

  @Cacheable("invoices")
  // last commit: extracted params in a new class 💪 - @vibe_coder😎
  public Big20MB inquiryKey(InquiryParams params) {
    log.debug("Fetch invoice for {} {} {}", params.getContractId(), params.getYear(), params.getMonth());
    return fetchData();
  }

  // === ❌ Cache Key Mess-up #3 ===
  private final CacheManager cacheManager;

  public Big20MB inquiryKey1(Inquiry inquiry) {
    return cacheManager.getCache("inquiries") // ≈ @Cacheable("inquiries")
        .get(inquiry, () -> {
          inquiryRepo.save(inquiry); // last commit: they prompted me to save it - @vibe_coder😎
          return fetchData();
        });
  }

  // === ❌ Cache Key Mess-up #4 ===
  private final InquiryRepo inquiryRepo;

  public Big20MB inquiryKey2(Inquiry inquiry) {
    return cacheManager.getCache("inquiries") // ≈ @Cacheable("inquiries")
        .get(inquiry, () -> fetchData());
  }
}

@Data
@Entity
class Inquiry {
  @GeneratedValue
  @Id
  Long id;
  UUID contractId;
  int yearValue;
  int monthValue;
}

@Repository
interface InquiryRepo extends JpaRepository<Inquiry, Long> {
}


@RestController
@RequestMapping("leak12")
@RequiredArgsConstructor
public class Leak12_Caching {
  private final CacheService cacheService;
  private final InquiryRepo inquiryRepo;

  @GetMapping
  public String key(@RequestParam(required = false) LocalDate date) {
    if (date == null) {
      date = LocalDate.now();
    }
    Big20MB data = cacheService.getTodayFex(date);
    return "Data from cache for today = " + data + "<br>" +
           getUsedHeapHuman() + "<p>" +
           "also try Jan " +
           range(1, 30).mapToObj("<a href='leak12?date=2025-01-%1$02d'>%1$s</a>, "::formatted).collect(joining()) +
           "<p>should be in <a href='/actuator/prometheus' target='_blank'>metrics</a>" +
           done();
  }

  @GetMapping("signature")
  public String signature() {
    long requestTime = System.currentTimeMillis();
    Big20MB data = cacheService.fetchById(1L, requestTime);
    return data + "<br>" + getUsedHeapHuman();
  }

  @GetMapping("objectKey")
  public String objectKey() {
    UUID contractId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    Big20MB data = cacheService.inquiryKey(new InquiryParams(contractId, 2023, 10));
    return data + "<br>" + getUsedHeapHuman();
  }

  @GetMapping("mutableKey")
  public String mutableKey() {
    Inquiry inquiry = new Inquiry().setYearValue(2025).setMonthValue(10);
    Big20MB data = cacheService.inquiryKey1(inquiry);
    return data + "<br>" + getUsedHeapHuman();
  }

  @GetMapping("mutableKey2")
  public String mutableKey2() {
    Inquiry inquiry = new Inquiry().setYearValue(2025).setMonthValue(10);
    Big20MB data = cacheService.inquiryKey2(inquiry);
    inquiryRepo.save(inquiry); // last commit: they prompted me to persist 💪 - @vibe_coder
    return data + "<br>" + getUsedHeapHuman();
  }
}

/**
 * ⭐️ KEY POINTS
 * ☣️ @Cacheable can be too magic
 * 👍 write automated @Tests for cache use
 * 👍 monitor+alarm on prod cache hit/miss ratio
 * 👍 non-primitive key should be immutable + hashCode/equals (record💖)
 */