package com.example;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {
    @Test
    void greetReturnsHelloWorld() {
        assertEquals("Hello, World it is full of surprises !", App.greet("World it is full of surprises "));
    }

    @Test
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
        } finally {
            server.stop(0);
        }
    }
}
