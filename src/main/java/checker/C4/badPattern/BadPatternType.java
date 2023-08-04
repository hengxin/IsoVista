package checker.C4.badPattern;

import lombok.Getter;

@Getter
public enum BadPatternType {
    ThinAirRead("BP-a"),
    AbortedRead("BP-b"),
    FutureRead("BP-c"),
    NotMyOwnWrite("BP-d"),
    IntermediateRead("BP-e"),
    NonRepeatableRead("BP-f"),
    CyclicCO("BP-g"),
    FracturedReadCO("BP-h"),
    FracturedReadVO("BP-i"),
    WriteCOInitRead("BP-j"),
    COConflictVO("BP-k"),
    ConflictVO("BP-l");

    private final String code;

    BadPatternType(String code) {
        this.code = code;
    }
}
