package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.app.trust.PublicKeyDirectory;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports readiness only after current public trust can be read; no private state or process paths are exposed. */
@RestController
public final class ServiceHealth {
    private final String role;
    private final PublicKeyDirectory directory;

    /**
     * Supplies the fixed process role and public-only readiness dependency.
     *
     * @param role the configured role label
     * @param directory the current public trust projection
     */
    public ServiceHealth(String role, PublicKeyDirectory directory) {
        this.role = role;
        this.directory = directory;
    }

    /**
     * Refuses readiness while a pending mutation fences verification.
     *
     * @return fixed role and ready status
     */
    @GetMapping("/api/service/health")
    public Map<String, String> health() {
        directory.view();
        return Map.of("role", role, "status", "READY");
    }
}
