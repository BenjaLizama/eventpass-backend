package cl.eventpass.gateway;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizerTest {
    private String token(String type) {
        var enc=Base64.getUrlEncoder().withoutPadding();
        return "Bearer "+enc.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8))+"."
                +enc.encodeToString(("{\"tokenType\":\""+type+"\",\"iat\":"+(Instant.now().getEpochSecond()-10)+",\"exp\":"+(Instant.now().getEpochSecond()+600)+"}").getBytes(StandardCharsets.UTF_8))+".c2ln";
    }
    private Map<String,Object> call(Authorizer a,String route,String token) {
        return a.handleRequest(Map.of("routeKey",route,"headers",Map.of("Authorization",token)),null);
    }
    @Test void rolesAndFailClosed() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var role=new java.util.concurrent.atomic.AtomicReference<>("CUSTOMER");
        var status=new java.util.concurrent.atomic.AtomicInteger(200);
        server.createContext("/api/v1/auth/me",ex->{
            byte[] body=("{\"data\":{\"id\":\""+UUID.randomUUID()+"\",\"role\":\""+role.get()+"\",\"accountNonLocked\":true}}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(),body.length); ex.getResponseBody().write(body); ex.close();
        });
        server.start();
        var auth=new Authorizer(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
        try {
            assertEquals(true,call(auth,"GET /api/v1/orders/my-orders",token("ACCESS")).get("isAuthorized"));
            for(String route:List.of("POST /api/v1/events","PATCH /api/v1/tickets/{ticketCode}/use","POST /api/v1/admin/users/staff","PUT /api/v1/tickets/internal"))
                assertEquals(false,call(auth,route,token("ACCESS")).get("isAuthorized"));
            assertEquals(false,call(auth,"GET /api/v1/auth/me",token("REFRESH")).get("isAuthorized"));
            assertEquals(false,call(auth,"GET /api/v1/auth/me",token("SERVICE")).get("isAuthorized"));
            role.set("STAFF");
            assertEquals(true,call(auth,"PATCH /api/v1/tickets/{ticketCode}/use",token("ACCESS")).get("isAuthorized"));
            assertEquals(false,call(auth,"POST /api/v1/events",token("ACCESS")).get("isAuthorized"));
            role.set("ORGANIZER");
            assertEquals(true,call(auth,"POST /api/v1/events",token("ACCESS")).get("isAuthorized"));
            assertEquals(false,call(auth,"PATCH /api/v1/tickets/{ticketCode}/use",token("ACCESS")).get("isAuthorized"));
            for(int code:List.of(401,403,500)) {status.set(code);assertEquals(false,call(auth,"GET /api/v1/auth/me",token("ACCESS")).get("isAuthorized"));}
        } finally {server.stop(0);}
        assertEquals(false,call(auth,"GET /api/v1/auth/me",token("ACCESS")).get("isAuthorized"));
    }
    @Test void malformedAndUnsignedTokensAreRejected() throws Exception {
        for(String s:List.of("", "Bearer a.b.c", "Basic a", "Bearer a.b", "Bearer a.b.c,d")) {
            try {assertFalse(Authorizer.isAccessToken(s));} catch(Exception expected) { /* malformed JSON is denied by handler */ }
        }
        assertFalse(Authorizer.isAccessToken(token("REFRESH")));
    }
}
