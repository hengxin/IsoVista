package checker;

public enum IsolationLevel {
    READ_COMMITTED,
    REPEATABLE_READ,
    READ_ATOMICITY,
    CAUSAL_CONSISTENCY,
    SNAPSHOT_ISOLATION,
    SERIALIZABLE;
}
