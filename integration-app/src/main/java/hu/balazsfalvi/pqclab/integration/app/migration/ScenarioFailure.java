package hu.balazsfalvi.pqclab.integration.app.migration;

/** Stops a dependent scenario safely without copying provider, parser or filesystem messages into evidence. */
final class ScenarioFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

    /** Retains only a caller-owned fixed failure category. */
    ScenarioFailure(String code) {
        super(code);
        this.code = code;
    }

    /** @return the safe evidence category */
    String code() {
        return code;
    }
}
