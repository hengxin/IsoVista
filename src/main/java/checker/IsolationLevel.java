package checker;

public enum IsolationLevel {
    READ_COMMITTED,
    READ_ATOMICITY,
    CAUSAL_CONSISTENCY,
    SNAPSHOT_ISOLATION,
    SERIALIZABLE;
}
