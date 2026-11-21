package cl.eventpass.gateway;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** REQUEST authorizer: Auth verifies signature/session; this function enforces current route roles. */
public final class Authorizer implements RequestHandler<Map<String,Object>, Map<String,Object>> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> HUMAN_ROLES = Set.of("CUSTOMER","ORGANIZER","STAFF","SUPPORT","ADMIN");
    private final URI auth;
    private final HttpClient client;
    public Authorizer() { this(privateAuth(System.getenv("AUTH_SERVICE_URL"))); }
    Authorizer(URI authBase) {
        auth = authBase.resolve("/api/v1/auth/me");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private static URI privateAuth(String value) {
        URI uri = URI.create(Objects.requireNonNull(value, "AUTH_SERVICE_URL"));
        if (!"http".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().startsWith("internal-")
                || !uri.getHost().endsWith(".elb.amazonaws.com") || uri.getPort()!=8081
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) {
            throw new IllegalArgumentException("Se requiere el endpoint privado de Auth.");
        }
        return uri;
    }
    @Override public Map<String,Object> handleRequest(Map<String,Object> event, Context context) {
        if (context != null) context.getLogger().log("JWT authorization request\n");
        try {
            String route = Objects.toString(event.get("routeKey"), "");
            JsonNode allowed = policy().get(route);
            // No implicit allow for unknown routes, including internal service routes.
            if (allowed == null || allowed.isEmpty() || allowed.toString().contains("PUBLIC")) return denied();
            Object raw = event.get("headers");
            if (!(raw instanceof Map<?,?> headers)) return denied();
            var bearer = headers.entrySet().stream().filter(e -> "authorization".equalsIgnoreCase(Objects.toString(e.getKey())))
                    .map(e -> Objects.toString(e.getValue(), "")).toList();
            if (bearer.size()!=1 || !isAccessToken(bearer.getFirst())) return denied();
            var request = HttpRequest.newBuilder(auth).timeout(Duration.ofSeconds(4))
                    .header("Authorization",bearer.getFirst()).header("Accept","application/json").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200 || response.body().length()>65536) return denied();
            JsonNode user = JSON.readTree(response.body()).path("data");
            String id = user.path("id").asText("");
            UUID.fromString(id);
            String role = user.path("role").asText("");
            if (!HUMAN_ROLES.contains(role) || !user.path("accountNonLocked").asBoolean(false)) return denied();
            boolean permitted = false;
            for (JsonNode entry : allowed) if (role.equals(entry.asText()) || "AUTHENTICATED".equals(entry.asText())) permitted = true;
            if (!permitted) return denied();
            // No bearer tokens, personal data or secrets in the authorizer context/logs.
            return Map.of("isAuthorized",true,"context",Map.of("userId",id,"role",role));
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return denied(); // Timeouts, Redis/Auth failures and malformed data fail closed.
        }
    }
    static boolean isAccessToken(String value) throws Exception {
        if (value.length()>8192 || !value.matches("Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) return false;
        String[] parts = value.substring(7).split("\\.");
        JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
        if (!Set.of("HS256","HS384","HS512").contains(header.path("alg").asText())) return false;
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
        // Unverified parsing only rejects token types early. Auth must verify the signature before any allow.
        return "ACCESS".equals(claims.path("tokenType").asText())
                && claims.path("exp").asLong(0)>Instant.now().getEpochSecond()
                && claims.path("iat").asLong(Long.MAX_VALUE)<=Instant.now().getEpochSecond();
    }
    private static JsonNode policy() throws Exception {
        try (var stream = Authorizer.class.getResourceAsStream("/route-policy.json")) { return JSON.readTree(stream); }
    }
    private static Map<String,Object> denied() { return Map.of("isAuthorized",false); }
}
