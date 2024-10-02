import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AggregationServer class acts as the main server that listens for incoming connections
 * from weather stations and clients. It aggregates weather data and handles synchronization
 * using Lamport clocks. It also includes a data expiration mechanism to remove outdated data.
 */
public class AggregationServer {

    // Atomic integer for the Lamport clock to ensure thread-safe operations.
    private AtomicInteger lamportClock = new AtomicInteger(0);

    // ConcurrentHashMap to store weather data from different weather stations.
    // The key is the station ID, and the value is the WeatherStationData object.
    private ConcurrentHashMap<String, WeatherStationData> weatherDataMap = new ConcurrentHashMap<>();

    // Port number on which the server will listen for incoming connections.
    private int port;

    /**
     * Constructor to initialize the server with the specified port number.
     *
     * @param port The port number to listen on.
     */
    public AggregationServer(int port) {
        this.port = port;
    }

    /**
     * Main method to start the server. It accepts an optional command-line argument
     * for the port number; otherwise, it uses the default port 8080.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        int portNumber = 8080; // Default port
        if (args.length > 0) {
            portNumber = Integer.parseInt(args[0]);
        }
        AggregationServer server = new AggregationServer(portNumber);
        server.startServer();
    }

    /**
     * Starts the server by initiating the data expiration task and setting up
     * the server socket to accept client connections.
     */
    public void startServer() {
        // Start the data expiration task to remove outdated weather data periodically.
        startDataExpirationTask();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation Server is listening on port " + port);

            // Continuously accept new client connections.
            while (true) {
                Socket socket = serverSocket.accept();
                // For each connection, start a new ServerHandler thread to handle the client.
                new ServerHandler(socket, this).start();
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
        }
    }

    /**
     * Updates the server's Lamport clock based on the received clock from a client.
     * Ensures synchronized logical time across distributed systems.
     *
     * @param receivedClock The Lamport clock value received from the client.
     * @return The updated Lamport clock value.
     */
    public synchronized int updateLamportClock(int receivedClock) {
        int currentClock = lamportClock.get();
        // Update the Lamport clock to the maximum of current and received clocks plus one.
        lamportClock.set(Math.max(currentClock, receivedClock) + 1);
        return lamportClock.get();
    }

    /**
     * Retrieves the current value of the Lamport clock.
     *
     * @return The current Lamport clock value.
     */
    public int getLamportClock() {
        return lamportClock.get();
    }

    /**
     * Provides access to the weather data map.
     *
     * @return The ConcurrentHashMap containing weather station data.
     */
    public ConcurrentHashMap<String, WeatherStationData> getWeatherDataMap() {
        return weatherDataMap;
    }

    /**
     * Starts a scheduled task that periodically removes outdated weather data
     * that hasn't been updated in the last 30 seconds.
     */
    private void startDataExpirationTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Schedule the task to run every 30 seconds.
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            // Remove data entries that are older than 30 seconds.
            weatherDataMap.values().removeIf(data ->
                    currentTime - data.getLastUpdateTime() > 30000); // 30 seconds
        }, 30, 30, TimeUnit.SECONDS);
    }
}

/**
 * ServerHandler class extends Thread to handle individual client connections.
 * It processes incoming requests, updates the Lamport clock, and interacts
 * with the AggregationServer to update or retrieve weather data.
 */
class ServerHandler extends Thread {
    private Socket socket;
    private AggregationServer server;

    /**
     * Constructor to initialize the ServerHandler with the client socket and server reference.
     *
     * @param socket The client socket connection.
     * @param server The AggregationServer instance.
     */
    public ServerHandler(Socket socket, AggregationServer server) {
        this.socket = socket;
        this.server = server;
    }

    /**
     * The main run method of the thread, which handles the client's request.
     * It reads the request, updates the Lamport clock, and calls appropriate
     * methods to handle PUT or GET requests.
     */
    public void run() {
        try (
            // BufferedReader to read input from the client.
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            // PrintWriter to send output to the client.
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        ) {
            // Read the request line (e.g., "PUT / HTTP/1.1").
            String requestLine = in.readLine();
            if (requestLine == null) return;

            // Read and parse the HTTP headers.
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while (!(headerLine = in.readLine()).equals("")) {
                String[] headerParts = headerLine.split(": ");
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]);
                }
            }

            // Extract the Lamport clock value from the headers.
            int clientLamportClock = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            // Update the server's Lamport clock based on the client's clock.
            int serverLamportClock = server.updateLamportClock(clientLamportClock);

            // Determine the type of request (PUT or GET) and handle accordingly.
            if (requestLine.startsWith("PUT")) {
                handlePutRequest(in, headers, out, serverLamportClock);
            } else if (requestLine.startsWith("GET")) {
                handleGetRequest(out, serverLamportClock);
            } else {
                // Send a 400 Bad Request response for unsupported methods.
                sendResponse(out, "HTTP/1.1 400 Bad Request", "Invalid request method.", serverLamportClock);
            }

        } catch (IOException e) {
            System.out.println("Exception in handler: " + e.getMessage());
        }
    }

    /**
     * Handles PUT requests from clients to update weather data.
     * Parses the JSON body, updates the weather data map, and sends a response.
     *
     * @param in                BufferedReader to read input from the client.
     * @param headers           Map containing HTTP headers.
     * @param out               PrintWriter to send output to the client.
     * @param serverLamportClock The server's updated Lamport clock value.
     * @throws IOException If an I/O error occurs.
     */
    private void handlePutRequest(BufferedReader in, Map<String, String> headers,
                                  PrintWriter out, int serverLamportClock) throws IOException {
        // Get the Content-Length header to determine the size of the request body.
        int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));

        // Read the JSON data from the request body.
        char[] bodyChars = new char[contentLength];
        in.read(bodyChars);
        String body = new String(bodyChars);

        // Parse the JSON data into a Map.
        Map<String, String> weatherData = parseJson(body);

        // Extract the station ID from the data.
        String stationId = weatherData.get("id");
        if (stationId != null) {
            // Retrieve or create a WeatherStationData object for this station ID.
            WeatherStationData stationData = server.getWeatherDataMap()
                    .computeIfAbsent(stationId, k -> new WeatherStationData());

            // Synchronize on the stationData object to prevent concurrent modifications.
            synchronized (stationData) {
                // Update the station data with the new weather data.
                stationData.updateData(weatherData);
                // Update the Lamport clock and last update time.
                stationData.setLamportClock(serverLamportClock);
                stationData.setLastUpdateTime(System.currentTimeMillis());
            }

            // Send a 200 OK response indicating successful data update.
            sendResponse(out, "HTTP/1.1 200 OK", "Data updated successfully.", serverLamportClock);
        } else {
            // If the station ID is missing, send a 400 Bad Request response.
            sendResponse(out, "HTTP/1.1 400 Bad Request", "Station ID is missing.", serverLamportClock);
        }
    }

    /**
     * Handles GET requests from clients to retrieve aggregated weather data.
     * Compiles data from all weather stations and sends it back in JSON format.
     *
     * @param out               PrintWriter to send output to the client.
     * @param serverLamportClock The server's updated Lamport clock value.
     */
    private void handleGetRequest(PrintWriter out, int serverLamportClock) {
        // StringBuilder to construct the JSON array of weather data.
        StringBuilder responseBody = new StringBuilder();
        responseBody.append("[");

        // Iterator over the values in the weather data map.
        Iterator<WeatherStationData> iterator = server.getWeatherDataMap().values().iterator();
        while (iterator.hasNext()) {
            WeatherStationData data = iterator.next();
            synchronized (data) {
                // Append the JSON representation of each station's data.
                responseBody.append(data.toJson());
                if (iterator.hasNext()) {
                    responseBody.append(","); // Add a comma between JSON objects.
                }
            }
        }
        responseBody.append("]");

        // Send a 200 OK response with the aggregated weather data in JSON format.
        sendResponse(out, "HTTP/1.1 200 OK", responseBody.toString(), serverLamportClock, "application/json");
    }

    /**
     * Parses a JSON-formatted string into a Map of key-value pairs.
     * Note: This is a simple parser and may not handle complex JSON structures.
     *
     * @param json The JSON string to parse.
     * @return A Map containing the parsed key-value pairs.
     */
    private Map<String, String> parseJson(String json) {
        Map<String, String> dataMap = new HashMap<>();
        json = json.trim();
        // Check if the JSON string starts with '{' and ends with '}'.
        if (json.startsWith("{") && json.endsWith("}")) {
            // Remove the enclosing braces.
            json = json.substring(1, json.length() - 1);
            // Split the string into key-value pairs.
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                // Split each pair into key and value.
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    // Remove any enclosing quotes and trim whitespace.
                    String key = kv[0].trim().replaceAll("\"", "");
                    String value = kv[1].trim().replaceAll("\"", "");
                    // Add the key-value pair to the map.
                    dataMap.put(key, value);
                }
            }
        }
        return dataMap;
    }

    /**
     * Sends an HTTP response to the client with the specified status line, body,
     * and Lamport clock value. Uses "text/plain" as the default content type.
     *
     * @param out           PrintWriter to send output to the client.
     * @param statusLine    The HTTP status line (e.g., "HTTP/1.1 200 OK").
     * @param body          The body of the response.
     * @param lamportClock  The Lamport clock value to include in the headers.
     */
    private void sendResponse(PrintWriter out, String statusLine, String body, int lamportClock) {
        // Use "text/plain" as the default content type.
        sendResponse(out, statusLine, body, lamportClock, "text/plain");
    }

    /**
     * Sends an HTTP response to the client with the specified status line, body,
     * Lamport clock value, and content type.
     *
     * @param out           PrintWriter to send output to the client.
     * @param statusLine    The HTTP status line.
     * @param body          The body of the response.
     * @param lamportClock  The Lamport clock value to include in the headers.
     * @param contentType   The content type of the response body.
     */
    private void sendResponse(PrintWriter out, String statusLine, String body, int lamportClock, String contentType) {
        out.println(statusLine);
        out.println("Content-Type: " + contentType);
        out.println("Lamport-Clock: " + lamportClock);
        out.println("Content-Length: " + body.getBytes().length);
        out.println(); // Blank line to indicate end of headers.
        out.println(body); // Response body.
    }
}

/**
 * WeatherStationData class holds the weather data for a single weather station.
 * It includes methods to update data, convert data to JSON, and keep track of
 * the Lamport clock and last update time.
 */
class WeatherStationData {
    // Map to store the weather data key-value pairs.
    private Map<String, String> data = new HashMap<>();
    // Lamport clock value associated with the last update.
    private int lamportClock;
    // Timestamp of the last update in milliseconds.
    private long lastUpdateTime;
    // LinkedList to keep track of the recent updates (up to 20 entries).
    private LinkedList<Map<String, String>> recentUpdates = new LinkedList<>();

    /**
     * Updates the weather data with new data from the weather station.
     * Also maintains a history of recent updates.
     *
     * @param newData The new data to update.
     */
    public synchronized void updateData(Map<String, String> newData) {
        // Update the main data map with new key-value pairs.
        data.putAll(newData);
        // Add the new data to the list of recent updates.
        recentUpdates.add(new HashMap<>(newData));
        // Ensure the recentUpdates list doesn't exceed 20 entries.
        if (recentUpdates.size() > 20) {
            recentUpdates.removeFirst();
        }
    }

    /**
     * Converts the weather data to a JSON-formatted string.
     *
     * @return A JSON representation of the weather data.
     */
    public synchronized String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        // Iterator over the data entries.
        Iterator<Map.Entry<String, String>> iterator = data.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            // Append each key-value pair in JSON format.
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            if (iterator.hasNext()) {
                json.append(","); // Add a comma between key-value pairs.
            }
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Retrieves the Lamport clock value associated with the last data update.
     *
     * @return The Lamport clock value.
     */
    public int getLamportClock() {
        return lamportClock;
    }

    /**
     * Sets the Lamport clock value for the current data.
     *
     * @param lamportClock The new Lamport clock value.
     */
    public void setLamportClock(int lamportClock) {
        this.lamportClock = lamportClock;
    }

    /**
     * Retrieves the timestamp of the last data update.
     *
     * @return The last update time in milliseconds.
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Sets the timestamp for the last data update.
     *
     * @param lastUpdateTime The new last update time in milliseconds.
     */
    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}
