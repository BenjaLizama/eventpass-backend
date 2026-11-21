package cl.eventpass.e2e;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrganizerOwnershipIT {
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient client=HttpClient.newHttpClient();
    private String base(String name,int port){return System.getenv().getOrDefault(name,"http://localhost:"+port);}
    private HttpResponse<String> request(String method,String base,String path,String token,Object body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body!=null)b.header("Content-Type","application/json");
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> r,int expected)throws Exception{assertEquals(expected,r.statusCode());return json.readTree(r.body()).path("data");}
    @Test void organizerCannotModifyAnotherOrganizersEvent()throws Exception{
        String auth=base("AUTH_BASE_URL",8081),events=base("EVENTS_BASE_URL",8082);
        String admin=data(request("POST",auth,"/api/v1/auth/login",null,Map.of("email",System.getenv("E2E_ADMIN_EMAIL"),"password",System.getenv("E2E_ADMIN_PASSWORD"))),200).path("access_token").asText();
        List<String> sessions=new ArrayList<>(List.of(admin));
        try{
            List<String> organizers=new ArrayList<>();
            for(int i=0;i<2;i++){
                String email="organizer-security-"+UUID.randomUUID()+"@example.com";
                var credentials=Map.of("email",email,"password","Password123!");
                data(request("POST",auth,"/api/v1/admin/users/organizer",admin,credentials),201);
                String token=data(request("POST",auth,"/api/v1/auth/login",null,credentials),200).path("access_token").asText();
                organizers.add(token);sessions.add(token);
            }
            var venue=data(request("POST",events,"/api/v1/venues",admin,Map.of("name","Security venue "+UUID.randomUUID(),"address","Calle 123","city","Santiago","country","Chile","capacity",5)),201);
            var event=data(request("POST",events,"/api/v1/events",organizers.getFirst(),Map.of("title","Private draft "+UUID.randomUUID(),"category","CONCERT","startDate",Instant.now().plusSeconds(86400).toString(),"endDate",Instant.now().plusSeconds(90000).toString(),"venueId",venue.path("id").asText(),"ticketCategories",List.of(Map.of("name","General","price",1000,"totalCapacity",5,"maxPerUser",1)))),201);
            String path="/api/v1/events/"+event.path("id").asText();
            assertEquals(404,request("GET",events,path,null,null).statusCode());
            assertEquals(404,request("GET",events,path,organizers.get(1),null).statusCode());
            assertEquals(200,request("GET",events,path,organizers.getFirst(),null).statusCode());
            for(String operation:List.of("/publish","/cancel")) assertEquals(403,request("PATCH",events,path+operation,organizers.get(1),null).statusCode());
            assertEquals(403,request("PUT",events,path,organizers.get(1),Map.of("title","Forbidden change")).statusCode());
            data(request("PATCH",events,path+"/publish",organizers.getFirst(),null),200);
            assertEquals(200,request("GET",events,path,null,null).statusCode());
            data(request("PATCH",events,path+"/cancel",organizers.getFirst(),null),200);
        }finally{for(String session:sessions)assertEquals(200,request("POST",auth,"/api/v1/auth/logout",session,null).statusCode());}
    }
}
