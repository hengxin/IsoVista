package checker.C4.util;

public interface Clock<T> {
    boolean isLessThanOrEqual(T clock);
    boolean isEqual(T clock);
    void join(T clock);
    void incrementBy(int val);
}
