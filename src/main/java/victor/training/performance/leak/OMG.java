package victor.training.performance.leak;

public class OMG {
  public static void main(String[] args) {
    method(1L,1L);
    method(100L,100L);
    method(127L,127L);
    method(128L,128L);
  }
  public static void method(Long id1, Long id2) {
    // Integer same
    if (id1 == id2) {
      System.out.println(id1+"WTF?!! Eclipse/NetBeans Forever");
    }
  }
}
