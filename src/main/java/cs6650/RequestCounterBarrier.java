package cs6650;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class RequestCounterBarrier {
    final static private int NUMTHREADS = 100; // Change this to the number of threads you want
    private int count = 0;
    private CountDownLatch completed = new CountDownLatch(NUMTHREADS);

    public void sendRequest() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet("http://localhost:8080/cs6650_hw3_a_Web_exploded/hello"); // Replace with your servlet URL
            client.execute(request);
            synchronized(this) {
                count++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            completed.countDown();
        }
    }

    public int getVal() {
        synchronized(this) {
            return this.count;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final RequestCounterBarrier counter = new RequestCounterBarrier();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUMTHREADS; i++) {
            new Thread(counter::sendRequest).start();
        }

        counter.completed.await(); // Wait for all threads to complete

        long endTime = System.currentTimeMillis();
        System.out.println("Time taken: " + (endTime - startTime) + " ms");
        System.out.println("Requests completed: " + counter.getVal());
    }
}