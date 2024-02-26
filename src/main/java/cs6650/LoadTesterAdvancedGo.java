package cs6650;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LoadTesterAdvancedGo {

    private static final HttpClient httpClient = HttpClient.newBuilder().build();
    private static final List<RequestRecord> requestRecords = new ArrayList<>();

    static class RequestRecord {
        long startTime;
        String requestType;
        long latency; // in milliseconds
        int responseCode;

        public RequestRecord(long startTime, String requestType, long latency, int responseCode) {
            this.startTime = startTime;
            this.requestType = requestType;
            this.latency = latency;
            this.responseCode = responseCode;
        }
    }

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

        // After completing all requests:
        displayStatistics();
    }

    private static void runRequests(int count, String serverURI) {
        for (int i = 0; i < count; i++) {
            // Example for a POST request
            HttpRequest postRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverURI + "/IGORTON/AlbumStore/1.0.0/albums"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"Artist\": \"Example Artist\", \"Title\": \"Example Title\", \"Year\": \"2024\"}"))
                    .build();
            long postStartTime = System.currentTimeMillis();
            HttpResponse<String> postResponse = sendRequestWithRetry(postRequest);
            long postEndTime = System.currentTimeMillis();
            if (postResponse != null) {
                requestRecords.add(new RequestRecord(postStartTime, "POST", postEndTime - postStartTime, postResponse.statusCode()));
            }

            // Example for a GET request
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverURI + "/IGORTON/AlbumStore/1.0.0/albums/123"))
                    .GET()
                    .build();
            long getStartTime = System.currentTimeMillis();
            HttpResponse<String> getResponse = sendRequestWithRetry(getRequest);
            long getEndTime = System.currentTimeMillis();
            if (getResponse != null) {
                requestRecords.add(new RequestRecord(getStartTime, "GET", getEndTime - getStartTime, getResponse.statusCode()));
            }
        }
    }


    private static HttpResponse<String> sendRequestWithRetry(HttpRequest request) {
        int retries = 5;
        while (retries-- > 0) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response; // Success
                } else {
                    System.out.println("Received HTTP error: " + response.statusCode() + ". Retrying...");
                    Thread.sleep(1000); // Wait before retrying
                }
            } catch (IOException | InterruptedException e) {
                if (retries == 0) {
                    System.out.println("Failed after retries: " + e.getMessage());
                }
            }
        }
        return null;
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
    private static void displayStatistics() {
        // Filter records by request type
        List<RequestRecord> postRecords = requestRecords.stream().filter(r -> r.requestType.equals("POST")).collect(Collectors.toList());
        List<RequestRecord> getRecords = requestRecords.stream().filter(r -> r.requestType.equals("GET")).collect(Collectors.toList());

        // Write to CSV
        try (PrintWriter writer = new PrintWriter("response_times.csv")) {
            writer.println("Request Type,Start Time,Latency,Response Code");
            for (RequestRecord record : requestRecords) {
                writer.println(record.requestType + "," + record.startTime + "," + record.latency + "," + record.responseCode);
            }
        } catch (IOException e) {
            System.out.println("Error writing to CSV file: " + e.getMessage());
        }

        // Calculate throughput
        long totalDuration = System.currentTimeMillis() - requestRecords.get(0).startTime;
        double throughput = (double) requestRecords.size() / (totalDuration / 1000.0);

        // Display statistics for POST requests
        System.out.println("POST Request Statistics:");
        displayRequestTypeStatistics(postRecords);

        // Display statistics for GET requests
        System.out.println("GET Request Statistics:");
        displayRequestTypeStatistics(getRecords);

        // Display throughput
        System.out.println("Throughput: " + throughput + " requests per second");
    }

    private static void displayRequestTypeStatistics(List<RequestRecord> records) {
        if (records.isEmpty()) {
            System.out.println("No records to display.");
            return;
        }
        // Calculating statistics
        double mean = records.stream().mapToLong(r -> r.latency).average().orElse(Double.NaN);
        long min = records.stream().mapToLong(r -> r.latency).min().orElse(Long.MIN_VALUE);
        long max = records.stream().mapToLong(r -> r.latency).max().orElse(Long.MAX_VALUE);

        // For median and p99, sort the latencies
        List<Long> sortedLatencies = records.stream().map(r -> r.latency).sorted().collect(Collectors.toList());
        long median = sortedLatencies.get(sortedLatencies.size() / 2);
        long p99 = sortedLatencies.get((int) (sortedLatencies.size() * 0.99));

        // Displaying the statistics
        System.out.println("Mean Response Time: " + mean + " ms");
        System.out.println("Median Response Time: " + median + " ms");
        System.out.println("99th Percentile Response Time: " + p99 + " ms");
        System.out.println("Min Response Time: " + min + " ms");
        System.out.println("Max Response Time: " + max + " ms");
    }

}
