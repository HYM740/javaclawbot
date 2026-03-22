package providers;

@FunctionalInterface
public interface CancelChecker {
    boolean isCancelled();
}