package cs6650;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTesterServlet {

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
                Path imagePath = Paths.get("C:\\Users\\whl96\\Downloads\\images.jpg");
                byte[] imageData = Files.readAllBytes(imagePath);
                String boundary = "---------------------------" + System.currentTimeMillis();

                HttpRequest postRequest = HttpRequest.newBuilder()
                        .uri(URI.create(serverURI + "hw6-part2_war/albums"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(ofMimeMultipartData(Map.of(
                                "artist", "Example Artist",
                                "title", "Example Title",
                                "year", "2024"
                        ), imageData, boundary))
                        .build();
                sendRequestWithRetry(postRequest);

                // GET request remains the same
                HttpRequest getRequest = HttpRequest.newBuilder()
                        .uri(URI.create(serverURI + "hw6-part2_war/albums?albumID=" + (i + 1)))
                        .GET()
                        .build();
                sendRequestWithRetry(getRequest);
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    private static HttpRequest.BodyPublisher ofMimeMultipartData(Map<Object, Object> data, byte[] fileData, String boundary) throws IOException {
        var byteArrays = new ArrayList<byte[]>();
        byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=").getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            byteArrays.add(separator);

            byteArrays.add(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        // Add file data
        String filePart = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"dummy.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
        byteArrays.add(filePart.getBytes(StandardCharsets.UTF_8));
        byteArrays.add(fileData);
        byteArrays.add(("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8));

        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
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
