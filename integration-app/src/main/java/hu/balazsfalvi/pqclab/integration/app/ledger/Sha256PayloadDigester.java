package hu.balazsfalvi.pqclab.integration.app.ledger;

import hu.balazsfalvi.pqclab.integration.core.PayloadDigester;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Uses standard JCA hashing for exact-byte idempotency, independently of signature algorithms. */
public final class Sha256PayloadDigester implements PayloadDigester {
    /** Creates a stateless adapter; each call allocates independent digest state. */
    public Sha256PayloadDigester() {}

    /** {@inheritDoc} */
    @Override
    public String digest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("The runtime lacks required SHA-256 support.", failure);
        }
    }
}
