package victor.training.performance.leak;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.*;
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

@SuppressWarnings("DataFlowIssue")
@Service
@Slf4j
@RequiredArgsConstructor
class CacheService {
  private Big20MB fetchData() {
    return new Big20MB();
  }

  // === ☢️ RISK: DIY Cache (NIH Syndrome?) ===
  // use caffeine / ehcache(disk)
  // static
  private final Map<LocalDate, Big20MB> fexCache = synchronizedMap(new HashMap<>());
  public Big20MB getForeignExchangeRates(LocalDate date) {
    return fexCache.computeIfAbsent(date, d -> {
      log.debug("Fetch FEX for date: {}", date);
      return fetchData();
    });
  }

  @Bean
  MeterBinder fexCacheMetrics() {// ⇒ set ALARMS 🔔 on these in production
    // TODO AI: any better way?
    return registry -> Gauge.builder("fex_cache_size", fexCache, Map::size).register(registry);
    // + entry ttl + hit ratio + ...
  }

  // === ❌ Cache Key Mess-up #1 ===
  // AOP proxy returns the previous value for the same parameter(s)
  @Cacheable("signature")
  public Big20MB fetchById(Long id, long requestTime) {
    // blame: added request time param -- 😎vibe-coded with Haiku
    log.debug("Fetch contract id:{} at:{}", id, requestTime);
    return fetchData();
  }

  // === ❌ Cache Key Mess-up #2 ===
  record InquiryParams(long contractId, int year, int month) {
  }

  @Cacheable("invoices")
  public Big20MB inquiryKey(InquiryParams params) { // blame: extracted params in a new class 😎
    log.debug("Fetch invoice for {} {} {}", params.contractId(), params.year(), params.month());
    return fetchData();
  }

  // === ❌ Cache Key Mess-up #3 [hard⭐️] ===
  private final CacheManager cacheManager;

  public Big20MB inquiryKey1(Inquiry inquiry) {
    return cacheManager.getCache("inquiries") // ≈ @Cacheable("inquiries")
        .get(inquiry, () -> {
          inquiryRepo.save(inquiry); // blame: saved it 😎
          // id is assigned by save, but hashCode/equals
          // are generated on all fields,
          // so the key is different before/after save → cache miss
          return fetchData();
        });
  }

  // === ❌ Cache Key Mess-up #4 ===
  private final InquiryRepo inquiryRepo;

  public Big20MB inquiryKey2(Inquiry inquiry) {
    return cacheManager.getCache("inquiries")
        .get(inquiry, () -> fetchData()); // @see caller
  }
}

@Data
@Entity // generated hash/equals is dangerous if mutates
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
    Big20MB data = cacheService.getForeignExchangeRates(date);
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
    Long contractId = 42L;
    InquiryParams params = new InquiryParams(contractId, 2023, 10);
    Big20MB data = cacheService.inquiryKey(params);
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