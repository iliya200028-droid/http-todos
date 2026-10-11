package ru.sudachen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class HttpTodosApp {

    private static final String BASE_URL =
            "https://jsonplaceholder.typicode.com";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build()) {

            String newTodo = MAPPER.writeValueAsString(Map.of(
                    "title", "Learn Java HttpClient",
                    "completed", false
            ));

            String updatedTodo = MAPPER.writeValueAsString(Map.of(
                    "title", "Homework completed",
                    "completed", true
            ));

            HttpRequest getRequest = request("/todos")
                    .GET()
                    .build();

            HttpRequest postRequest = request("/todos")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(newTodo))
                    .build();

            HttpRequest putRequest = request("/todos/1")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(updatedTodo))
                    .build();

            HttpRequest deleteRequest = request("/todos/1")
                    .DELETE()
                    .build();

            CompletableFuture<Void> getFuture =
                    execute(client, getRequest, "GET");

            CompletableFuture<Void> postFuture =
                    execute(client, postRequest, "POST");

            CompletableFuture<Void> putFuture =
                    execute(client, putRequest, "PUT");

            CompletableFuture<Void> deleteFuture =
                    execute(client, deleteRequest, "DELETE");

            CompletableFuture.allOf(
                    getFuture,
                    postFuture,
                    putFuture,
                    deleteFuture
            ).get();

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Application interrupted.");

        } catch (ExecutionException exception) {
            System.err.println("Request failed: "
                    + rootCause(exception));

        } catch (IOException | URISyntaxException exception) {
            System.err.println("Failed to prepare requests: "
                    + exception.getMessage());

        } catch (RuntimeException exception) {
            System.err.println("Application error: "
                    + exception.getMessage());
        }
    }

    private static HttpRequest.Builder request(String path)
            throws URISyntaxException {

        return HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
    }

    private static CompletableFuture<Void> execute(
            HttpClient client,
            HttpRequest request,
            String operation) {

        return client.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                )
                .thenAccept(response -> {
                    try {
                        printResponse(operation, response);
                    } catch (IOException exception) {
                        throw new CompletionException(exception);
                    }
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        System.err.println(operation + " failed: "
                                + rootCause(error));
                    }
                });
    }

    private static synchronized void printResponse(
            String operation,
            HttpResponse<String> response) throws IOException {

        int status = response.statusCode();

        System.out.println();
        System.out.println("=== " + operation + " ===");
        System.out.println("HTTP status: " + status);

        if (status < 200 || status >= 300) {
            throw new IOException(
                    "Unexpected HTTP status " + status
                            + ". Response: " + response.body()
            );
        }

        if ("DELETE".equals(operation)) {
            System.out.println("Todo deleted successfully.");
            return;
        }

        JsonNode json = MAPPER.readTree(response.body());

        if ("GET".equals(operation)) {
            if (json == null || !json.isArray()) {
                throw new IOException("Expected a JSON array.");
            }

            for (int i = 0; i < Math.min(5, json.size()); i++) {
                JsonNode todo = json.get(i);

                System.out.println(
                        todo.path("id").asInt() + ": "
                                + todo.path("title").asText()
                );
            }
        } else {
            System.out.println(
                    MAPPER.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(json)
            );
        }
    }

    private static String rootCause(Throwable error) {
        Throwable cause = error;

        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        return cause.getClass().getSimpleName()
                + ": " + cause.getMessage();
    }
}
