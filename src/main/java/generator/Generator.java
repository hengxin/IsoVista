package generator;

import history.History;

public interface Generator<KeyType, ValType> {
    History<KeyType, ValType> generate();
}
