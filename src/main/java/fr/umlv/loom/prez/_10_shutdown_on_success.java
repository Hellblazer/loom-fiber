package fr.umlv.loom.prez;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.util.concurrent.ExecutionException;

public interface _10_shutdown_on_success {
  static void main(String[] args) throws InterruptedException, ExecutionException {
    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<Integer>()) {
      var start = System.currentTimeMillis();
      var future1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      var future2 = scope.fork(() -> {
        Thread.sleep(42);
        return 2;
      });
      scope.join();
      var end = System.currentTimeMillis();
      System.out.println("elapsed " + (end - start));
      //System.out.println(future1.resultNow());
      //System.out.println(future2.resultNow());
      System.out.println(scope.result());
    }
  }
}
