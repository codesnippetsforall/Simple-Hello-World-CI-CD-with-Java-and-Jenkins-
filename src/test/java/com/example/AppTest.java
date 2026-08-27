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
    @Order(2)
    @DisplayName("greet returns Hello message for given name")
    void greetReturnsHelloWorld() {
        assertEquals("Hello, World it is full of surprises !", App.greet("World it is full of surprises "));
    }

    @Test
    @Order(1)
    @DisplayName("HTTP GET / returns Hello World body")
    void httpGetRootReturnsHelloWorld() throws Exception {
        HttpServer server = App.startServer(0);
        try {
            int port = server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("Hello, World it is full of surprises !", response.body());
            assertTrue(response.body().startsWith("Hello,"));
        } finally {
            server.stop(0);
        }
    }
}
