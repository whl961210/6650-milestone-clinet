package cs6650;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTester {

    private static final HttpClient httpClient = HttpClient.newBuilder().build();

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 4) {
            System.out.println("Usage: java LoadTester <threadGroupSize> <numThreadGroups> <delayInSeconds> <serverURI>");
            return;
        }

        int threadGroupSize = Integer.parseInt(args[0]);
        int numThreadGroups = Integer.parseInt(args[1]);
        int delay = Integer.parseInt(args[2]);
        String serverURI = args[3];

        // Initialization phase
        ExecutorService initExecutor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            initExecutor.submit(() -> runRequests(100, serverURI));
        }
        shutdownAndAwaitTermination(initExecutor);

        // Take startTime timestamp
        long startTime = System.currentTimeMillis();

        ExecutorService mainExecutor = Executors.newCachedThreadPool();
        AtomicInteger totalRequests = new AtomicInteger();
        for (int i = 0; i < numThreadGroups; i++) {
            for (int j = 0; j < threadGroupSize; j++) {
                mainExecutor.submit(() -> {
                    runRequests(1000, serverURI);
                    totalRequests.addAndGet(2000); // Counting both POST and GET
                });
            }
            Thread.sleep(delay * 1000L);
        }
        shutdownAndAwaitTermination(mainExecutor);

        // Take endTime timestamp and calculate metrics
        long endTime = System.currentTimeMillis();
        long wallTime = (endTime - startTime) / 1000; // seconds
        double throughput = totalRequests.get() / (double) wallTime;

        System.out.println("Wall Time: " + wallTime + " seconds");
        System.out.println("Throughput: " + throughput + " requests/second");
    }

    private static void runRequests(int count, String serverURI) {
        for (int i = 0; i < count; i++) {
            try {
                HttpRequest postRequest = HttpRequest.newBuilder()
                        .uri(URI.create(serverURI + "/IGORTON/AlbumStore/1.0.0/albums"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"Artist\": \"Example Artist\", \"Title\": \"Example Title\", \"Year\": \"2024\"}"))
                        .build();
                sendRequestWithRetry(postRequest);

                HttpRequest getRequest = HttpRequest.newBuilder()
                        .uri(URI.create(serverURI + "/IGORTON/AlbumStore/1.0.0/albums/123"))
                        .GET()
                        .build();
                sendRequestWithRetry(getRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void sendRequestWithRetry(HttpRequest request) throws InterruptedException {
        int retries = 5;
        while (retries-- > 0) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    break; // Success
                } else {
                    System.out.println("Received HTTP error: " + response.statusCode() + ". Retrying...");
                    Thread.sleep(1000); // Wait before retrying
                }
            } catch (IOException e) {
                if (retries == 0) {
                    System.out.println("Failed after retries: " + e.getMessage());
                }
            }
        }
    }

    private static void shutdownAndAwaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
