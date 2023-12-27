package collector;

import lombok.Getter;

public enum IsolationLevel {
    TRANSACTION_READ_UNCOMMITTED(1),
    TRANSACTION_READ_COMMITTED(2),
    TRANSACTION_REPEATABLE_READ(4),
    TRANSACTION_SERIALIZATION(8);

    @Getter
    int constant;

    private IsolationLevel(int constant) {
        this.constant = constant;
    }
}
