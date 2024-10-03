package com.weather.app;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class ContentServer {

    private static LamportClock lamportClock = new LamportClock();
    private static final int RETRY_LIMIT = 3;
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ContentServer <server_url> <file_path>");
            return;
        }

        String serverUrl = args[0];
        String filePath = args[1];

        try {
            boolean success = sendDataWithRetry(serverUrl, filePath, RETRY_LIMIT);
            if (!success) {
                System.out.println("Failed to upload data after " + RETRY_LIMIT + " retries.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean sendDataWithRetry(String serverUrl, String filePath, int retries) {
        int attempt = 0;
        while (attempt < retries) {
            try {
                sendData(serverUrl, filePath);
                return true; // Successful upload
            } catch (IOException e) {
                attempt++;
                System.out.println("Failed to send data (attempt " + attempt + "). Retrying...");
            }
        }
        return false;
    }

    public static void sendData(String serverUrl, String filePath) throws IOException {
        Map<String, String> dataMap = readDataFromFile(filePath);
        String jsonData = mapToJson(dataMap);

        lamportClock.tick();

        URL url = new URL(serverUrl);
        Socket socket = createSocket(url);

        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send PUT request
            out.write("PUT / HTTP/1.1\r\n");
            out.write("Host: " + url.getHost() + "\r\n");
            out.write("Content-Type: application/json\r\n");
            out.write("Content-Length: " + jsonData.length() + "\r\n");
            out.write("Lamport-Clock: " + lamportClock.getClock() + "\r\n");
            out.write("\r\n");
            out.write(jsonData);
            out.flush();

            // Read response
            String statusLine = in.readLine();
            if (statusLine == null) return;
            System.out.println("Response: " + statusLine);

            Map<String, String> headers = new HashMap<>();
            String line;
            while (!(line = in.readLine()).equals("")) {
                String[] header = line.split(": ");
                headers.put(header[0], header[1]);
            }

            // Update Lamport clock
            int serverLamportClock = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            lamportClock.update(serverLamportClock);

            StringBuilder responseBody = new StringBuilder();
            while (in.ready() && (line = in.readLine()) != null) {
                responseBody.append(line);
            }

            System.out.println("Server Response Body: " + responseBody.toString());
        } finally {
            socket.close();
        }
    }

    protected static Socket createSocket(URL url) throws IOException {
        return new Socket(url.getHost(), url.getPort());
    }

    public static Map<String, String> readDataFromFile(String filePath) throws IOException {
        Map<String, String> dataMap = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        for (String line : lines) {
            String[] kv = line.split(":", 2); // Limit to 2 to handle values with colons
            if (kv.length >= 2) {
                dataMap.put(kv[0].trim(), kv[1].trim());
            }
        }
        return dataMap;
    }

    public static String mapToJson(Map<String, String> map) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(map);
    }
}
