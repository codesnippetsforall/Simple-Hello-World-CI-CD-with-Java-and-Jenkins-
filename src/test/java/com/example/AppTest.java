package com.example;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppTest {
    @Test
    @Order(1)
    @DisplayName("greet returns Hurray message for given name")
    void greetReturnsHurrayWorld() {
        assertEquals("Hurray, World it is full of surprises !", App.greet("World it is full of surprises "));
    }

    @Test
    @Order(2)
    @DisplayName("HTTP GET / returns Hurray World body")
    void httpGetRootReturnsHurrayWorld() throws Exception {
        HttpServer server = App.startServer(0);
        try {
            HttpResponse<String> response = get(server, "/");
            assertEquals(200, response.statusCode());
            assertEquals("Hurray, World it is full of surprises !", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Order(3)
    @DisplayName("HTTP GET /api/health returns JSON status")
    void httpGetHealthReturnsJson() throws Exception {
        HttpServer server = App.startServer(0);
        try {
            HttpResponse<String> response = get(server, "/api/health");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"UP\""));
            assertTrue(response.body().contains("\"service\":\"Hurrayworldwithjava\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Order(4)
    @DisplayName("HTTP GET /api/greet returns JSON message")
    void httpGetGreetReturnsJson() throws Exception {
        HttpServer server = App.startServer(0);
        try {
            HttpResponse<String> response = get(server, "/api/greet?name=Java");
            assertEquals(200, response.statusCode());
            assertEquals("{\"message\":\"Hurray, Java!\"}", response.body());
        } finally {
            server.stop(0);
        }
    }

    private HttpResponse<String> get(HttpServer server, String path) throws Exception {
        int port = server.getAddress().getPort();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
