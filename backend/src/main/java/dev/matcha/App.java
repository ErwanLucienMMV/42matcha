package dev.matcha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import spark.Service;

public final class App {
    private App() {}

    public static void main(String[] args) throws Exception {
        Config config = Config.from(System.getenv());
        Database database = new Database(config);
        Migrations.apply(database);
        Service http = create(config, database);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            http.stop();
            http.awaitStop();
            database.close();
        }, "matcha-shutdown"));
    }

    static Service create(Config config, Database database) {
        ObjectMapper json = new ObjectMapper();
        Service http = Service.ignite().port(config.port()).threadPool(32, 4, 30000);
        http.before((request, response) -> {
            response.type("application/json; charset=utf-8");
            response.header("X-Content-Type-Options", "nosniff");
            response.header("Cache-Control", "no-store");
            if (!request.requestMethod().equals("GET") && !request.requestMethod().equals("HEAD") && !request.requestMethod().equals("OPTIONS")) {
                String origin = request.headers("Origin");
                if (origin != null && !origin.equals(config.appUrl())) throw ApiError.forbidden("This request came from another site.");
            }
            if (request.contentLength() > 8 * 1024 * 1024) throw new ApiError(413, "request_too_large", "This request is too large.");
        });
        Api api = new Api(http, database, json);
        Accounts accounts = new Accounts(config);
        accounts.registration(api);
        http.get("/api/health", (request, response) -> Map.of("status", "ok"), json::writeValueAsString);
        http.get("/api/ready", (request, response) -> {
            boolean ready = database.healthy();
            response.status(ready ? 200 : 503);
            return Map.of("status", ready ? "ready" : "unavailable");
        }, json::writeValueAsString);
        http.notFound((request, response) -> json.writeValueAsString(Map.of("error", "Route not found")));
        http.exception(ApiError.class, (error, request, response) -> {
            response.status(error.status);
            try { response.body(json.writeValueAsString(Map.of("error", error.getMessage(), "code", error.code))); }
            catch (com.fasterxml.jackson.core.JsonProcessingException impossible) { response.body("{\"error\":\"Request rejected\"}"); }
        });
        http.exception(Exception.class, (error, request, response) -> {
            response.status(500);
            response.body("{\"error\":\"An unexpected error occurred\"}");
            org.slf4j.LoggerFactory.getLogger(App.class).error("Request failed: {} {}", request.requestMethod(), request.pathInfo(), error);
        });
        http.init();
        http.awaitInitialization();
        return http;
    }
}
