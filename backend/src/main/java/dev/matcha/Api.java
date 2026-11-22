package dev.matcha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Service;

public final class Api {
    public record Image(byte[] content, String mediaType) {}
    public record Context(Request request, Response response, Connection connection, long userId) {}
    @FunctionalInterface public interface Handler { Object run(Context context) throws Exception; }
    @FunctionalInterface public interface Authentication { long check(Request request, Connection connection, boolean write) throws Exception; }

    private final Service http;
    private final Database database;
    private final ObjectMapper json;
    private Authentication authentication = (request, connection, write) -> { throw new ApiError(401, "authentication_required", "Please sign in."); };

    public Api(Service http, Database database, ObjectMapper json) {
        this.http = http;
        this.database = database;
        this.json = json;
    }

    public void authentication(Authentication value) { authentication = value; }
    public void get(String path, boolean authenticated, Handler handler) { route("GET", path, authenticated, handler); }
    public void post(String path, boolean authenticated, Handler handler) { route("POST", path, authenticated, handler); }
    public void put(String path, boolean authenticated, Handler handler) { route("PUT", path, authenticated, handler); }
    public void delete(String path, boolean authenticated, Handler handler) { route("DELETE", path, authenticated, handler); }

    public void image(String path, Handler handler) {
        http.get(path, (request, response) -> {
            Image image = Sql.transaction(database, connection -> (Image) handler.run(new Context(request, response, connection, authentication.check(request, connection, false))));
            response.type(image.mediaType());
            response.raw().setContentLength(image.content().length);
            response.raw().getOutputStream().write(image.content());
            return null;
        });
    }

    private void route(String method, String path, boolean authenticated, Handler handler) {
        Route route = (request, response) -> Sql.transaction(database, connection -> {
            long userId = authenticated ? authentication.check(request, connection, !method.equals("GET")) : 0;
            return handler.run(new Context(request, response, connection, userId));
        });
        switch (method) {
            case "GET" -> http.get(path, route, json::writeValueAsString);
            case "POST" -> http.post(path, route, json::writeValueAsString);
            case "PUT" -> http.put(path, route, json::writeValueAsString);
            case "DELETE" -> http.delete(path, route, json::writeValueAsString);
            default -> throw new IllegalArgumentException("Unsupported method");
        }
    }
}
