package com.weather.app;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class GETClient {

    private LamportClock lamportClock = new LamportClock();
    private static final int RETRY_LIMIT = 3;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server_url>");
            return;
        }

        String serverUrl = args[0];
        GETClient client = new GETClient();

        try {
            boolean success = client.sendGetRequestWithRetry(serverUrl, RETRY_LIMIT);
            if (!success) {
                System.out.println("Failed to fetch data after " + RETRY_LIMIT + " retries.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean sendGetRequestWithRetry(String serverUrl, int retries) {
        int attempt = 0;
        while (attempt < retries) {
            try {
                sendGetRequest(serverUrl);
                return true; // Successful GET
            } catch (IOException e) {
                attempt++;
                System.out.println("Failed to get data (attempt " + attempt + "). Retrying...");
            }
        }
        return false;
    }

    public void sendGetRequest(String serverUrl) throws IOException {
        lamportClock.tick();
        URL url = normalizeUrl(serverUrl);

        Socket socket = createSocket(url);

        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send GET request
            out.write("GET / HTTP/1.1\r\n");
            out.write("Host: " + url.getHost() + "\r\n");
            out.write("Lamport-Clock: " + lamportClock.getClock() + "\r\n");
            out.write("\r\n");
            out.flush();

            // Read response
            String statusLine = in.readLine();
            if (statusLine == null) return;
            System.out.println("Response: " + statusLine);

            Map<String, String> headers = new HashMap<>();
            String line;
            int contentLength = 0;
            while (!(line = in.readLine()).equals("")) {
                String[] header = line.split(": ");
                headers.put(header[0], header[1]);
                if (header[0].equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(header[1]);
                }
            }

            // Update Lamport clock
            int serverLamportClock = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            lamportClock.update(serverLamportClock);

            char[] bodyChars = new char[contentLength];
            in.read(bodyChars);
            String body = new String(bodyChars);

            // Parse and display data
            parseAndDisplay(body);
        } finally {
            socket.close();
        }
    }

    private URL normalizeUrl(String serverUrl) throws MalformedURLException {
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://" + serverUrl;
        }
        return new URL(serverUrl);
    }

    protected Socket createSocket(URL url) throws IOException {
        return new Socket(url.getHost(), url.getPort());
    }

    public void parseAndDisplay(String jsonString) {
        Gson gson = new Gson();
        JsonArray jsonArray = JsonParser.parseString(jsonString).getAsJsonArray();

        for (JsonElement element : jsonArray) {
            JsonObject jsonObject = element.getAsJsonObject();
            System.out.println("Weather Data Entry:");
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue().getAsString());
            }
            System.out.println("-----");
        }
    }
}
