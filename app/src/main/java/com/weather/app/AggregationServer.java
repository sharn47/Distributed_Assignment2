package com.weather.app;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class AggregationServer {

    private static final int DEFAULT_PORT = 4567;
    private static final String DATA_FILE = "weatherData.json";
    private static final String TEMP_FILE = "weatherData.tmp";
    private static final long EXPIRATION_TIME_MILLIS = 30_000; // 30 seconds
    private static final int MAX_ENTRIES = 20; // Maximum 20 entries
    private static final LamportClock lamportClock = new LamportClock();
    private static final Object fileLock = new Object(); // For synchronizing file writes

    // Data structures to store weather data and timestamps of content servers
    public static final Map<String, JsonObject> weatherData = new LinkedHashMap<>();
    private static final Map<String, Long> serverTimestamps = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws IOException {

        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port: " + DEFAULT_PORT);
            }
        }

        // Load weather data from persistent storage
        loadFromFile();

        // Periodically clean up expired entries
        scheduler.scheduleAtFixedRate(AggregationServer::cleanExpiredData, 10, 10, TimeUnit.SECONDS);

        // Start the server
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation Server is running on port " + port);

            ExecutorService threadPool = Executors.newFixedThreadPool(10); // Use thread pool

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleClient(clientSocket));
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            System.out.println("Received request: " + requestLine);
            String[] requestParts = requestLine.split(" ", 3);
            String method = requestParts.length >= 1 ? requestParts[0] : "";
            String path = requestParts.length >= 2 ? requestParts[1] : "";

            Map<String, String> headers = new HashMap<>();
            String line;
            int clientLamportClock = 0;

            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int separatorIndex = line.indexOf(":");
                if (separatorIndex != -1) {
                    String headerName = line.substring(0, separatorIndex).trim();
                    String headerValue = line.substring(separatorIndex + 1).trim();
                    headers.put(headerName, headerValue);

                    if (headerName.equalsIgnoreCase("Lamport-Clock")) {
                        clientLamportClock = Integer.parseInt(headerValue);
                    }
                }
            }

            lamportClock.update(clientLamportClock);

            if ("PUT".equalsIgnoreCase(method)) {
                handlePutRequest(in, out, socket.getRemoteSocketAddress().toString(), headers);
            } else if ("GET".equalsIgnoreCase(method)) {
                handleGetRequest(out);
            } else {
                out.println("HTTP/1.1 400 Bad Request");
                out.println("Lamport-Clock: " + lamportClock.getClock());
                out.println();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handlePutRequest(BufferedReader in, PrintWriter out, String contentServer, Map<String, String> headers) throws IOException {
        lamportClock.tick();

        int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
        if (contentLength == 0) {
            out.println("HTTP/1.1 204 No Content");
            out.println("Lamport-Clock: " + lamportClock.getClock());
            out.println();
            return;
        }

        char[] bodyData = new char[contentLength];
        in.read(bodyData, 0, contentLength);
        String jsonData = new String(bodyData);

        if (!isValidJson(jsonData)) {
            out.println("HTTP/1.1 400 Bad Request");
            out.println("Lamport-Clock: " + lamportClock.getClock());
            out.println();
            return;
        }

        JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
        jsonObject.addProperty("origin", contentServer);
        jsonObject.addProperty("timestamp", Instant.now().toEpochMilli());

        String entryId = jsonObject.get("id").getAsString();
        synchronized (fileLock) {
            weatherData.put(entryId, jsonObject);
            if (weatherData.size() > MAX_ENTRIES) {
                Iterator<String> iterator = weatherData.keySet().iterator();
                iterator.next();
                iterator.remove(); // Remove the oldest entry
            }
        }
        serverTimestamps.put(contentServer, Instant.now().toEpochMilli());

        synchronized (fileLock) {
            try {
                writeToTempFile(weatherData);
                if (commitTempFile()) {
                    out.println("HTTP/1.1 200 OK");
                    out.println("Lamport-Clock: " + lamportClock.getClock());
                    out.println();
                } else {
                    out.println("HTTP/1.1 500 Internal Server Error");
                    out.println("Lamport-Clock: " + lamportClock.getClock());
                    out.println();
                }
            } catch (IOException e) {
                out.println("HTTP/1.1 500 Internal Server Error");
                out.println("Lamport-Clock: " + lamportClock.getClock());
                out.println();
            }
        }
    }

    private static void handleGetRequest(PrintWriter out) throws IOException {
        lamportClock.tick();
        String jsonResponse = convertToJson(weatherData);

        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonResponse.length());
        out.println("Lamport-Clock: " + lamportClock.getClock());
        out.println();
        out.print(jsonResponse);
        out.flush();
    }

    private static void cleanExpiredData() {
        long currentTime = Instant.now().toEpochMilli();
        synchronized (fileLock) {
            weatherData.entrySet().removeIf(entry -> {
                JsonObject jsonObject = entry.getValue();
                long timestamp = jsonObject.get("timestamp").getAsLong();
                return (currentTime - timestamp > EXPIRATION_TIME_MILLIS);
            });
        }
        try {
            writeToTempFile(weatherData);
            commitTempFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadFromFile() {
        File dataFile = new File(DATA_FILE);
        if (dataFile.exists()) {
            try {
                String fileContent = new String(Files.readAllBytes(dataFile.toPath()));
                JsonArray jsonArray = JsonParser.parseString(fileContent).getAsJsonArray();
                for (JsonElement element : jsonArray) {
                    JsonObject jsonObject = element.getAsJsonObject();
                    String entryId = jsonObject.get("id").getAsString();
                    weatherData.put(entryId, jsonObject);
                }
            } catch (IOException e) {
                System.out.println("Error loading data from file: " + e.getMessage());
            }
        }
    }

    private static boolean isValidJson(String jsonData) {
        try {
            JsonElement jsonElement = JsonParser.parseString(jsonData);
            return jsonElement.isJsonObject();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private static void writeToTempFile(Map<String, JsonObject> data) throws IOException {
        try (FileWriter fileWriter = new FileWriter(TEMP_FILE)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonArray jsonArray = new JsonArray();
            for (JsonObject jsonObject : data.values()) {
                jsonArray.add(jsonObject);
            }
            gson.toJson(jsonArray, fileWriter);
        }
    }

    private static boolean commitTempFile() {
        File tempFile = new File(TEMP_FILE);
        File finalFile = new File(DATA_FILE);

        try {
            Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            System.out.println("Error while moving temp file to final file: " + e.getMessage());
            return false;
        }
    }

    public static String convertToJson(Map<String, JsonObject> weatherData) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Collection<JsonObject> dataCollection = weatherData.values();
        return gson.toJson(dataCollection);
    }
}
