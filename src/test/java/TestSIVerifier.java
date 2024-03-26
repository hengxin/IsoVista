import checker.PolySI.verifier.SIVerifier;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static history.Operation.Type.READ;
import static history.Operation.Type.WRITE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSIVerifier {
	@Test
	void readCommitted() {
		var h = (new TestLoader(
			Set.of(0, 1),
			Map.of(0, List.of(0, 1),
				   1, List.of(2)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1)),
				   1, List.of(Triple.of(WRITE, "x", 2),
							  Triple.of(WRITE, "y", 2)),
				   2, List.of(Triple.of(READ, "y", 2),
							  Triple.of(READ, "x", 1)))
		));

		var s = new SIVerifier<>(h.loadHistory(null));
		assertFalse(s.audit());
	}

	@Test
	void repeatableRead() {
		var h = (new TestLoader(
			Set.of(0, 1),
			Map.of(0, List.of(0, 1),
				1, List.of(2)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1)),
				1, List.of(Triple.of(WRITE, "x", 2)),
				2, List.of(Triple.of(READ, "x", 1),
					Triple.of(READ, "x", 2)))));

		assertFalse(new SIVerifier<>(h.loadHistory(null)).audit());
	}

	@Test
	void readMyWrites() {
		var h = (new TestLoader(
			Set.of(0, 1),
			Map.of(0, List.of(0),
				1, List.of(1, 2)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1),
					Triple.of(WRITE, "y", 1)),
				1, List.of(Triple.of(READ, "x", 1),
					Triple.of(WRITE, "y", 2)),
				2, List.of(Triple.of(READ, "x", 1),
					Triple.of(READ, "y", 1)))));

		assertFalse(new SIVerifier<>(h.loadHistory(null)).audit());
	}

	@Test
	void repeatableRead2() {
		var h = (new TestLoader(
			Set.of(0, 1, 2),
			Map.of(0, List.of(0),
				1, List.of(1),
				2, List.of(2)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1),
					Triple.of(WRITE, "y", 1)),
				1, List.of(Triple.of(WRITE, "x", 2),
					Triple.of(WRITE, "y", 2)),
				2, List.of(Triple.of(READ, "x", 1),
					Triple.of(READ, "y", 2)))));

		assertFalse(new SIVerifier<>(h.loadHistory(null)).audit());
	}

	@Test
	void causal() {
		var h = (new TestLoader(
			Set.of(0, 1, 2, 3),
			Map.of(0, List.of(0),
				1, List.of(1),
				2, List.of(2),
				3, List.of(3)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1)),
				1, List.of(Triple.of(READ, "x", 2),
					Triple.of(WRITE, "y", 1)),
				2, List.of(Triple.of(READ, "x", 1),
					Triple.of(WRITE, "x", 2)),
				3, List.of(Triple.of(READ, "x", 1),
					Triple.of(READ, "y", 1)))));

		assertFalse(new SIVerifier<>(h.loadHistory(null)).audit());
	}

	@Test
	void prefix() {
		var h = (new TestLoader(
			Set.of(0, 1, 2, 3, 4),
			Map.of(0, List.of(0),
				1, List.of(1),
				2, List.of(2),
				3, List.of(3),
				4, List.of(4)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1),
					Triple.of(WRITE, "y", 1)),
				1, List.of(Triple.of(READ, "x", 1),
					Triple.of(WRITE, "x", 2)),
				2, List.of(Triple.of(READ, "x", 2),
					Triple.of(READ, "y", 1)),
				3, List.of(Triple.of(READ, "y", 1),
					Triple.of(WRITE, "y", 2)),
				4, List.of(Triple.of(READ, "y", 2),
					Triple.of(READ, "x", 1)))));

		assertFalse(new SIVerifier<>(h.loadHistory(null)).audit());
	}

	@Test
	void conflict() {
		var h = (new TestLoader(
			Set.of(0, 1, 2),
			Map.of(0, List.of(0),
				1, List.of(1),
				2, List.of(2)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1)),
				1, List.of(Triple.of(READ, "x", 1),
					Triple.of(WRITE, "x", 2)),
				2, List.of(Triple.of(READ, "x", 1),
					Triple.of(WRITE, "x", 3)))));

		assertFalse(new SIVerifier<>(h.loadHistory(null)).audit());
	}

	@Test
	void serializability() {
		var h = (new TestLoader(
			Set.of(0, 1, 2),
			Map.of(0, List.of(0),
				1, List.of(1),
				2, List.of(2)),
			Map.of(0, List.of(Triple.of(WRITE, "x", 1),
					Triple.of(WRITE, "y", 1)),
				1, List.of(Triple.of(READ, "x", 1),
					Triple.of(READ, "y", 1),
					Triple.of(WRITE, "x", 2)),
				2, List.of(Triple.of(READ, "x", 1),
					Triple.of(READ, "y", 1),
					Triple.of(WRITE, "y", 2)))));

		assertTrue(new SIVerifier<>(h.loadHistory(null)).audit());
	}

    @Test
    void tidb1() {
        var h = new TestLoader(
            Set.of(0, 1, 2),
            Map.of(0, List.of(0),
                    1, List.of(1),
                    2, List.of(2)),
            Map.of(0, List.of(Triple.of(WRITE, "x", 0),
                              Triple.of(WRITE, "y", 0),
                              Triple.of(WRITE, "z", 0)),
                    1, List.of(Triple.of(READ, "x", 0),
                               Triple.of(WRITE, "y", 1),
                               Triple.of(WRITE, "z", 1)),
                    2, List.of(Triple.of(READ, "y", 0),
                               Triple.of(WRITE, "x", 1),
                               Triple.of(WRITE, "z", 2))));

        assertFalse(new SIVerifier<>(h.loadHistory(null)).audit());
    }

    @Test
    void example1() {
        var h = new TestLoader(
            Set.of(0, 1, 2),
            Map.of(0, List.of(6, 1),
                1, List.of(5, 4),
                2, List.of(3, 2)),
            Map.of(
                1, List.of(
                    Triple.of(WRITE, "x", 1),
                    Triple.of(WRITE, "w", 2),
                    Triple.of(READ, "y", 1)
                ),
                2, List.of(
                    Triple.of(WRITE, "x", 2),
                    Triple.of(WRITE, "y", 2),
                    Triple.of(READ, "z", 1)
                ),
                3, List.of(Triple.of(WRITE, "y", 1)),
                4, List.of(
                    Triple.of(WRITE, "z", 2),
                    Triple.of(READ, "w", 1)
                ),
                5, List.of(Triple.of(WRITE, "z", 1)),
                6, List.of(Triple.of(WRITE, "w", 1))
            )
        );

        assertTrue(new SIVerifier<>(h.loadHistory(null)).audit());
    }

    @Test
    void testInt1() {
        var loader = new TestLoader(
            Set.of(0),
            Map.of(0, List.of(0)),
            Map.of(0, List.of(
                Triple.of(WRITE, "x", 0),
                Triple.of(WRITE, "x", 1),
                Triple.of(READ, "x", 0)
            ))
        );

        assertFalse(new SIVerifier<>(loader.loadHistory(null)).audit());
    }

    @Test
    void testInt2() {
        var loader = new TestLoader(
            Set.of(0),
            Map.of(0, List.of(0)),
            Map.of(0, List.of(
                Triple.of(WRITE, "x", 0),
                Triple.of(WRITE, "x", 1),
                Triple.of(READ, "x", 1),
                Triple.of(READ, "x", 0)
            ))
        );

        assertFalse(new SIVerifier<>(loader.loadHistory(null)).audit());
    }

    @Test
    void testInt3() {
        var loader = new TestLoader(
            Set.of(0),
            Map.of(0, List.of(0)),
            Map.of(0, List.of(
                Triple.of(WRITE, "x", 0),
                Triple.of(READ, "x", 0),
                Triple.of(WRITE, "x", 1),
                Triple.of(READ, "x", 0)
            ))
        );

        assertFalse(new SIVerifier<>(loader.loadHistory(null)).audit());
    }

    @Test
    void testInt4() {
        var loader = new TestLoader(
            Set.of(0),
            Map.of(0, List.of(0)),
            Map.of(0, List.of(
                Triple.of(WRITE, "x", 1),
                Triple.of(READ, "x", 0)
            ))
        );

        assertFalse(new SIVerifier<>(loader.loadHistory(null)).audit());
    }

    @Test
    void testExample1() {
        var loader = new TestLoader(
            Set.of(0, 1, 2, 3, 4),
            Map.of(
                0, List.of(0, 5),
                1, List.of(1),
                2, List.of(2),
                3, List.of(3),
                4, List.of(4)
            ),
            Map.of(
                0, List.of(
                    Triple.of(WRITE, "x", 0),
                    Triple.of(WRITE, "y", 0)
                ),
                1, List.of(Triple.of(WRITE, "x", 1)),
                2, List.of(Triple.of(WRITE, "y", 1)),
                3, List.of(
                    Triple.of(READ, "x", 1),
                    Triple.of(READ, "y", 0)
                ),
                4, List.of(
                    Triple.of(READ, "x", 0),
                    Triple.of(READ, "y", 1)
                ),
                5, List.of(Triple.of(WRITE, "x", 2))
            )
        );

        assertFalse(new SIVerifier<>(loader.loadHistory(null)).audit());
    }

    @Test
    void testBP_D() {
        var loader = new TestLoader(
            Set.of(0, 1),
            Map.of(
                0, List.of(0),
                1, List.of(1)
            ),
            Map.of(
                0, List.of(
                    Triple.of(WRITE, "x", 0)
                ),
                1, List.of(
                    Triple.of(WRITE, "x", 1),
                    Triple.of(READ, "x", 0)
                )
            )
        );

        assertFalse(new SIVerifier<>(loader.loadHistory(null)).audit());
    }
}
