package hu.balazsfalvi.pqclab.integration.app.http;

import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Restricts the local workbench and APIs before requests reach signing or verification code. */
public final class LocalApiGuard implements Filter {
    private final java.util.Set<String> hosts;
    /** Creates the shared stateless guard for either loopback server. */
    public LocalApiGuard() {
        this(java.util.Set.of("localhost"));
    }

    /**
     * Adds only an explicit local Compose service name to the loopback Host allowlist.
     *
     * @param services the bootstrap-owned service host names
     */
    public LocalApiGuard(java.util.Set<String> services) {
        if (!java.util.Set.of("localhost", "127.0.0.1", "publisher", "receiver").containsAll(services)) {
            throw new IllegalArgumentException("Use only documented local service hosts.");
        }
        var allowed = new java.util.HashSet<>(services);
        allowed.add("localhost");
        allowed.add("127.0.0.1");
        hosts = java.util.Set.copyOf(allowed);
    }

    /** {@inheritDoc} */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse output = (HttpServletResponse) response;
        output.setHeader("Cache-Control", "no-store");
        output.setHeader("X-Content-Type-Options", "nosniff");
        output.setHeader("X-Frame-Options", "DENY");
        output.setHeader(
                "Content-Security-Policy",
                http.getRequestURI().startsWith("/api/")
                        ? "default-src 'none'; frame-ancestors 'none'"
                        : "default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'; "
                                + "img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        String host = http.getServerName();
        String origin = http.getHeader("Origin");
        boolean localHost = hosts.contains(host.toLowerCase(java.util.Locale.ROOT));
        // The request authority includes Docker's published loopback port; the container's local
        // listener port may differ. Host allowlisting above still rejects foreign DNS names.
        boolean trustedOrigin = origin == null || isWorkbenchOrigin(origin, http.getServerPort());
        // Exact loopback origins support the bundled UI and its fixed Vite development proxy.
        // Host validation also prevents a foreign DNS name from rebinding to this local signing API.
        if (!localHost || !trustedOrigin || "cross-site".equals(http.getHeader("Sec-Fetch-Site"))) {
            reject(output, 403, "BROWSER_ORIGIN_REJECTED");
        } else if (http.getContentLengthLong() > DsseLimits.MAX_JSON_BYTES) {
            reject(output, 413, "BODY_TOO_LARGE");
        } else {
            chain.doFilter(request, response);
        }
    }

    /** Accepts only this server's origin or the explicitly documented local development origin. */
    private boolean isWorkbenchOrigin(String origin, int serverPort) {
        return origin.equals("http://127.0.0.1:" + serverPort)
                || origin.equals("http://localhost:" + serverPort)
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:5173");
    }

    /** Writes only fixed server-owned text when a filter rejects before controller advice runs. */
    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter()
                .write("{\"title\":\"The integration request was rejected.\",\"status\":" + status + ",\"code\":\""
                        + code + "\"}");
    }
}
