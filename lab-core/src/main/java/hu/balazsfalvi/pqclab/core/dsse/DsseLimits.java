package hu.balazsfalvi.pqclab.core.dsse;

/** Defines the laboratory's bounded DSSE profile, rather than limits of DSSE itself. */
public final class DsseLimits {
    /** Maximum decoded payload size accepted by the laboratory. */
    public static final int MAX_PAYLOAD_BYTES = 65_536;
    /** Maximum UTF-8 size of a payload type. */
    public static final int MAX_PAYLOAD_TYPE_BYTES = 1_024;
    /** Maximum decoded size of one signature. */
    public static final int MAX_SIGNATURE_BYTES = 16_384;
    /** Maximum number of signature entries processed for one envelope. */
    public static final int MAX_SIGNATURES = 4;
    /** Maximum length of an unauthenticated key hint. */
    public static final int MAX_KEY_ID_CHARACTERS = 256;
    /** Maximum encoded JSON document size before parsing begins. */
    public static final int MAX_JSON_BYTES = 1_048_576;

    /** Prevents construction of a constants-only utility. */
    private DsseLimits() {}
}
