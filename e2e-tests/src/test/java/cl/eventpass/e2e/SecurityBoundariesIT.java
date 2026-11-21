package cl.eventpass.e2e;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecurityBoundariesIT {
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String base(String name,int port) {return System.getenv().getOrDefault(name,"http://localhost:"+port);}
    private HttpResponse<String> request(String method,String base,String path,String token,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(body!=null) builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private void denied(HttpResponse<String> response) {assertTrue(Set.of(401,403).contains(response.statusCode()),"Expected rejection, HTTP "+response.statusCode());}
    @Test void refreshTamperingAndCustomerPrivileges() throws Exception {
        String auth=base("AUTH_BASE_URL",8081),events=base("EVENTS_BASE_URL",8082),orders=base("ORDERS_BASE_URL",8083),tickets=base("TICKETS_BASE_URL",8084);
        String email="security-"+UUID.randomUUID()+"@example.com";
        var registration=request("POST",auth,"/api/v1/auth/register",null,Map.of("email",email,"password","Password123!","role","ADMIN"));
        if(registration.statusCode()==400) registration=request("POST",auth,"/api/v1/auth/register",null,Map.of("email",email,"password","Password123!"));
        assertEquals(201,registration.statusCode());
        JsonNode credentials=json.readTree(registration.body()).path("data");
        String access=credentials.path("access_token").asText(),refresh=credentials.path("refresh_token").asText();
        assertFalse(access.isBlank());assertFalse(refresh.isBlank());
        try {
            var me=request("GET",auth,"/api/v1/auth/me",access,null);assertEquals(200,me.statusCode());
            assertEquals("CUSTOMER",json.readTree(me.body()).path("data").path("role").asText());
            denied(request("GET",auth,"/api/v1/auth/me",refresh,null));
            denied(request("GET",orders,"/api/v1/orders/my-orders",refresh,null));
            denied(request("GET",tickets,"/api/v1/tickets/me",refresh,null));
            denied(request("GET",events,"/api/v1/events/my-events",refresh,null));
            var wrongRefresh=request("POST",auth,"/api/v1/auth/refresh",null,Map.of("refreshToken",access));
            assertTrue(Set.of(400,401,403).contains(wrongRefresh.statusCode()));
            assertEquals(200,request("POST",auth,"/api/v1/auth/refresh",null,Map.of("refreshToken",refresh)).statusCode());
            String[] parts=access.split("\\.");
            String tampered=parts[0]+"."+parts[1]+"."+(parts[2].charAt(0)=='A'?"B":"A")+parts[2].substring(1);
            for(var route:List.of(new String[]{auth,"/api/v1/auth/me"},new String[]{orders,"/api/v1/orders/my-orders"},new String[]{tickets,"/api/v1/tickets/me"},new String[]{events,"/api/v1/events/my-events"}))
                denied(request("GET",route[0],route[1],tampered,null));
            var event=Map.of("title","Denied security probe","category","CONCERT","startDate",Instant.now().plusSeconds(86400).toString(),"endDate",Instant.now().plusSeconds(90000).toString(),"venueId",UUID.randomUUID().toString(),"ticketCategories",List.of(Map.of("name","General","price",1000,"totalCapacity",1,"maxPerUser",1)));
            assertEquals(403,request("POST",events,"/api/v1/events",access,event).statusCode());
            assertEquals(403,request("PATCH",tickets,"/api/v1/tickets/"+UUID.randomUUID()+"/use",access,null).statusCode());
            assertEquals(403,request("POST",auth,"/api/v1/admin/users/staff",access,Map.of("email","forbidden@example.com","password","Password123!")).statusCode());
        } finally {assertEquals(200,request("POST",auth,"/api/v1/auth/logout",access,null).statusCode());}
        denied(request("GET",auth,"/api/v1/auth/me",access,null));
        denied(request("GET",orders,"/api/v1/orders/my-orders",access,null));
        denied(request("GET",tickets,"/api/v1/tickets/me",access,null));
    }
}
