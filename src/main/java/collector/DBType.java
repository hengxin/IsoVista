package collector;

import lombok.Getter;

@Getter
public enum DBType {
    MYSQL("mysql"),
    SQLITE("sqlite");

    private final String name;

    DBType(String name) {
        this.name = name;
    }
}
