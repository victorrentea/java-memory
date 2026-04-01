package victor.training.performance.leak;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import victor.training.performance.leak.CalculatorFactory.Calculator;
import victor.training.performance.leak.obj.Big20MB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static victor.training.performance.util.PerformanceUtil.done;
import static victor.training.performance.util.PerformanceUtil.sleepSeconds;

@RestController
@RequestMapping("leak2")
public class Leak2_Inner {
  @GetMapping("inner")
  public String endpoint() {
    Calculator calculator = new CalculatorFactory().create();
    work(calculator);


    return done();
  }
  private void work(Calculator calculator) {
    sleepSeconds(30); // time to take a heap dump
    // access the CalculatorFactory instance via reflection from the calculator instance param
    try {
      // Find the synthetic field referencing the enclosing instance
      var fields = calculator.getClass().getDeclaredFields();
      System.out.println("Calculator has " + fields.length + " declared fields");
      boolean found = false;
      for (var f : fields) {
        System.out.println("  Field: " + f.getName() + " type: " + f.getType().getSimpleName() + " synthetic: " + f.isSynthetic());
        if (f.getType() == CalculatorFactory.class) {
          f.setAccessible(true);
          CalculatorFactory outer = (CalculatorFactory) f.get(calculator);
          System.out.println("  ✅ Outer instance found via field '" + f.getName() + "': " + outer);
          found = true;
        }
      }
      if (!found) {
        System.out.println("  ⚠️ No outer reference field found! JDK " + Runtime.version() + " optimized it away (javac ≥21)");
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  //<editor-fold desc="Similar entry points /implem /subclass">
  @GetMapping("implem")
  public String implem() {
    Stream<String> supplier = new CalculatorFactory().anonymousVsLambdas(List.of("a"));
    sleepSeconds(30); // time to take a heap dump
    return supplier.toList().toString();
  }

  @GetMapping("subclass")
  public Map<String, Integer> subclass() {
    Map<String, Integer> map = new CalculatorFactory().mapInit();
    sleepSeconds(30); // time to take a heap dump
    return map;
  }
  //</editor-fold>
}
class CalculatorFactory {
  private final Big20MB bigMac = new Big20MB(); // 🍔
  private final String someSmallString = "a";
  public class Calculator {// TODO what's the connection with bigMac
    public String calculate() {
      return "Answer: " + someSmallString + 42;
    }
  }

  public Calculator create() {
    return new Calculator();
  }

  //<editor-fold desc="Lambdas vs Anonymous implementation">
  public Stream<String> anonymousVsLambdas(List<String> input) {
    return input.stream()
        .filter(new Predicate<String>() {
          @Override
          public boolean test(String s) {
            return !s.isBlank();
          }
        });
    // TODO how about ->, ::
  }
  //</editor-fold>
  {
    System.out.printf("it works😱😱");
  }
  //<editor-fold desc="Map init in Java <= 8">
  public Map<String, Integer> mapInit() {
//    return new HashMap<>() {{
//        put("one", 1);
//        put("two", 2);
//      }};
    return Map.of("one", 1, "two", 2);
  }
  //</editor-fold>
}

/**
 * ⭐️ KEY POINTS
 * - 👍 Prefer nested (static) over inner classes. Better: move to a separate .java file
 * - 😱 new Class(){} and new Interface(){} reference the instance of the containing class
 * - 😎 javac ≥21 ftw
 */