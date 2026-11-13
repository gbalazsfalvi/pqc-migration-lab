package hu.balazsfalvi.pqclab.integration.app.receiver;

import hu.balazsfalvi.pqclab.integration.app.trust.KeyRegistryView;
import hu.balazsfalvi.pqclab.integration.app.trust.PublicKeyDirectory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the receiver's actual public trust metadata, with no signing or lifecycle-write dependency. */
@RestController
public final class KeyRegistryController {
    private final PublicKeyDirectory directory;

    /**
     * Receives public-only verification metadata from trusted bootstrap.
     *
     * @param directory the same public directory used by reception
     */
    public KeyRegistryController(PublicKeyDirectory directory) {
        this.directory = directory;
    }

    /**
     * Reads the current revision and effective active/retiring/retired/revoked states.
     *
     * @return public key identities and fingerprints only
     */
    @GetMapping(path = "/api/receiver/keys", produces = MediaType.APPLICATION_JSON_VALUE)
    public KeyRegistryView keys() {
        return directory.view();
    }
}
