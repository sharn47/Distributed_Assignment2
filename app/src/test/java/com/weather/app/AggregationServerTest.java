package com.weather.app;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.Socket;
import static org.junit.jupiter.api.Assertions.*;

class AggregationServerTest {

    private static Thread serverThread;

    @BeforeAll
    static void startServer() {
        serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[] { "4568" });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Give some time for the server to start
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    static void stopServer() {
        serverThread.interrupt();
    }

    @Test
    void testPutAndGetRequest() throws IOException {
        // Send PUT request from ContentServer
        String jsonData = "{ \"id\": \"001\", \"name\": \"Test Station\", \"state\": \"Test State\" }";
        Socket socket = new Socket("localhost", 4568);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send the PUT request
        out.println("PUT /weather.json HTTP/1.1");
        out.println("Host: localhost");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonData.length());
        out.println("Lamport-Clock: 1");
        out.println();
        out.println(jsonData);

        // Read the response
        String response = in.readLine();
        assertTrue(response.contains("201") || response.contains("200")); // Either created or OK

        socket.close();

        // Send GET request from GETClient
        socket = new Socket("localhost", 4568);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send the GET request
        out.println("GET /weather.json HTTP/1.1");
        out.println("Host: localhost");
        out.println("Accept: application/json");
        out.println("Lamport-Clock: 2");
        out.println();

        // Read the response
        String statusLine = in.readLine();
        assertTrue(statusLine.contains("200")); // Ensure we get OK status

        // Read JSON body
        StringBuilder responseBody = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            responseBody.append(line);
        }

        assertTrue(responseBody.toString().contains("Test Station"));
        assertTrue(responseBody.toString().contains("Test State"));

        socket.close();
    }

    @Test
    void testPutInvalidJson() throws IOException {
        String invalidJsonData = "{ \"id\": }";  // Invalid JSON

        Socket socket = new Socket("localhost", 4568);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send PUT request with invalid JSON
        out.println("PUT /weather.json HTTP/1.1");
        out.println("Host: localhost");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + invalidJsonData.length());
        out.println("Lamport-Clock: 1");
        out.println();
        out.println(invalidJsonData);

        // Check response for 500 Internal Server Error
        String response = in.readLine();
        assertTrue(response.contains("500"));

        socket.close();
    }

    @Test
    void testGetRequestNoData() throws IOException {
        // Step 1: Simulate a GET request when no data has been added to the server
        Socket getSocket = new Socket("localhost", 4568);
        PrintWriter outGet = new PrintWriter(getSocket.getOutputStream(), true);
        BufferedReader inGet = new BufferedReader(new InputStreamReader(getSocket.getInputStream()));

        // Send GET request
        outGet.println("GET /weather.json HTTP/1.1");
        outGet.println("Host: localhost");
        outGet.println("Accept: application/json");
        outGet.println("Lamport-Clock: 1");
        outGet.println();

        // Step 2: Read the response status line
        String getStatusLine = inGet.readLine();
        assertTrue(getStatusLine.contains("200"), "Expected HTTP 200 OK for GET request when no data is available.");

        // Step 3: Read headers and skip them
        String line;
        StringBuilder responseBody = new StringBuilder();
        while ((line = inGet.readLine()) != null && !line.isEmpty()) {
            // Skipping headers
        }

        // Step 4: Read the response body
        while ((line = inGet.readLine()) != null) {
            responseBody.append(line);
        }

        // Step 5: Validate that the response body is an empty array (since no data has been added)
        assertEquals("[]", responseBody.toString().trim(), "Expected an empty response body when no data is available.");

        getSocket.close();
    }


    @Test
    void testPutRequestUpdatesClock() throws IOException {
        String jsonData = "{ \"id\": \"002\", \"name\": \"Update Test\", \"state\": \"Test State\" }";

        Socket socket = new Socket("localhost", 4568);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send PUT request
        out.println("PUT /weather.json HTTP/1.1");
        out.println("Host: localhost");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonData.length());
        out.println("Lamport-Clock: 5");
        out.println();
        out.println(jsonData);

        // Check if server response contains updated Lamport clock
        String response = in.readLine();
        String clockHeader = in.readLine();
        assertTrue(clockHeader.contains("Lamport-Clock"));
        assertTrue(Integer.parseInt(clockHeader.split(":")[1].trim()) >= 5);

        socket.close();
    }

    @Test
    void testGetRequestWithMultipleDataEntries() throws IOException {
        String jsonData1 = "{ \"id\": \"003\", \"name\": \"Station 1\", \"state\": \"State 1\" }";
        String jsonData2 = "{ \"id\": \"004\", \"name\": \"Station 2\", \"state\": \"State 2\" }";

        // Add two data entries
        sendPutRequest(jsonData1, 1);
        sendPutRequest(jsonData2, 2);

        // Send GET request to verify both entries
        Socket socket = new Socket("localhost", 4568);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println("GET /weather.json HTTP/1.1");
        out.println("Host: localhost");
        out.println("Accept: application/json");
        out.println("Lamport-Clock: 3");
        out.println();

        // Check response
        String statusLine = in.readLine();
        assertTrue(statusLine.contains("200"));

        // Check body for both stations
        StringBuilder responseBody = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            responseBody.append(line);
        }
        assertTrue(responseBody.toString().contains("Station 1"));
        assertTrue(responseBody.toString().contains("Station 2"));

        socket.close();
    }

    private void sendPutRequest(String jsonData, int clockValue) throws IOException {
        Socket socket = new Socket("localhost", 4568);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send PUT request
        out.println("PUT /weather.json HTTP/1.1");
        out.println("Host: localhost");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonData.length());
        out.println("Lamport-Clock: " + clockValue);
        out.println();
        out.println(jsonData);

        in.readLine();  // Read response

        socket.close();
    }
}