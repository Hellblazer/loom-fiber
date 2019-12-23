package fr.umlv.loom.test;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class JayTest {
  private static class TesterContinuation extends Continuation {
    private static final ContinuationScope SCOPE = new ContinuationScope("JayTest");
    
    private AssertionError error;
    
    private TesterContinuation(Runnable target) {
      super(SCOPE, target);
    }
    
    static TesterContinuation current() {
      return (TesterContinuation) Continuation.getCurrentContinuation(SCOPE);
    }

    static void yield(AssertionError error) {
      var current = current();
      current.error = error;
      Continuation.yield(SCOPE);
    }
    
    AssertionError call() {
      run();
      var error = this.error;
      this.error = null;
      return error;
    }
  }
  
  public static class TestError extends AssertionError {
    private static final long serialVersionUID = 1;
    private static final Set<String> HIDDEN_CLASS_NAMES =
        Set.of("fr.umlv.loom.test.JayTest", "fr.umlv.loom.test.JayTest$Query", "fr.umlv.loom.test.JayTest$TesterContinuation", "java.lang.Continuation");
    
    public TestError(String message) {
      super(message);
    }
    
    public TestError(String message, Throwable cause) {
      super(message, cause);
    }
    
    @Override
    public synchronized Throwable fillInStackTrace(ContinuationScope scope) {
      var stackWalker = StackWalker.getInstance(scope);
      setStackTrace(stackWalker.walk(frames -> frames.skip(7).flatMap(frame -> {
        if (HIDDEN_CLASS_NAMES.contains(frame.getClassName())) {
          return Stream.empty();
        }
        return Stream.of(frame.toStackTraceElement());
      }).toArray(StackTraceElement[]::new)));
      return this;
    }
  }
  
  @FunctionalInterface
  public interface Executable {
    void execute() throws Throwable;
  }
  
  public static void test(String description, Executable executable) {
    Objects.requireNonNull(description);
    Objects.requireNonNull(executable);

    var errors = new ArrayList<AssertionError>();
    var continuation = new TesterContinuation(() ->  {
      try {
        executable.execute();
      } catch(Throwable e) {
        errors.add(new TestError("unexpected exception", e));
      }
    });
    
    for(;;) {
      var error = continuation.call();
      if (error == null) {
        break;
      }
      errors.add(error);
    }
    
    if (errors.isEmpty()) {
      return;
    }
    var error = new TestError(description);
    errors.forEach(error::addSuppressed);
    
    if (TesterContinuation.current() != null) {
      TesterContinuation.yield(error);
    } else {
      throw error;
    }
  }
  
  private static void checkInsideTest() {
    if (TesterContinuation.current() == null) {
      throw new IllegalStateException("not enclosed in test()");
    }
  }
  
  private static <T> Query<T> expect0(T value) {
    checkInsideTest();
    return predicate -> new Result(predicate.test(value), "value " + value);
  }
  public static <T> Query<T> expect(T value) {
    return expect0(value);
  }
  public static <T> Query<Supplier<T>> expect(Supplier<? extends T> supplier) {
    return expect0((Supplier<T>)() -> supplier.get());
  }
  
  public record Result(boolean valid, String text) {
    public Result {
      Objects.requireNonNull(text);
    }
    
    public Result with(Function<? super String, ? extends String> mapper) {
      return new Result(valid, mapper.apply(text));
    }
  }
  
  @FunctionalInterface
  public interface Query<T> {
    public Result eval(Predicate<? super T> predicate);
    
    public default Query<T> not() {
      return predicate -> eval(Predicate.not(predicate)).with(text -> "not " + text);
    }
    
    public default void to(String message, Predicate<? super T> predicate) {
      Objects.requireNonNull(message);
      Objects.requireNonNull(predicate);
      var result = eval(predicate);
      if (!result.valid) {
        TesterContinuation.yield(new TestError(result.text + " " + message));
      }
    }
    
    public default void toBe(Object expected) {
      to("is equals to " + expected, value -> Objects.equals(value, expected));
    }    
    
    public default Query<?> returnValue() {
      return predicate -> {
        return eval (value -> {
          if (value instanceof Supplier<?> supplier) {
            return predicate.test(supplier.get());
          }
          return false;
        }).with(text -> "return " + text);
      };
    }
  } 
}