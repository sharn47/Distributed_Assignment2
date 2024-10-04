import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AggregationServer {

    // Lamport clock
    private AtomicInteger lamportClock = new AtomicInteger(0);

    // Weather data storage
    private ConcurrentHashMap<String, WeatherStationData> weatherDataMap = new ConcurrentHashMap<>();

    // Port number
    private int port;

    public AggregationServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int portNumber = 8080; // Default port
        if (args.length > 0) {
            portNumber = Integer.parseInt(args[0]);
        }
        AggregationServer server = new AggregationServer(portNumber);
        server.startServer();
    }

    public void startServer() {
        // Start data expiration thread
        startDataExpirationTask();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                new ServerHandler(socket, this).start();
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
        }
    }

    // Method to update Lamport clock
    public synchronized int updateLamportClock(int receivedClock) {
        int currentClock = lamportClock.get();
        lamportClock.set(Math.max(currentClock, receivedClock) + 1);
        return lamportClock.get();
    }

    // Method to get current Lamport clock
    public int getLamportClock() {
        return lamportClock.get();
    }

    // Methods to access weather data map
    public ConcurrentHashMap<String, WeatherStationData> getWeatherDataMap() {
        return weatherDataMap;
    }

    // Start data expiration task
    private void startDataExpirationTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            weatherDataMap.values().removeIf(data ->
                    currentTime - data.getLastUpdateTime() > 30000); // 30 seconds
        }, 30, 30, TimeUnit.SECONDS);
    }
}

// Thread to handle client requests
class ServerHandler extends Thread {
    private Socket socket;
    private AggregationServer server;

    public ServerHandler(Socket socket, AggregationServer server) {
        this.socket = socket;
        this.server = server;
    }

    public void run() {
        try (
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        ) {
            // Read request line
            String requestLine = in.readLine();
            if (requestLine == null) return;

            // Read headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while (!(headerLine = in.readLine()).equals("")) {
                String[] headerParts = headerLine.split(": ");
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]);
                }
            }

            // Get Lamport clock from headers
            int clientLamportClock = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            int serverLamportClock = server.updateLamportClock(clientLamportClock);

            // Handle request
            if (requestLine.startsWith("PUT")) {
                handlePutRequest(in, headers, out, serverLamportClock);
            } else if (requestLine.startsWith("GET")) {
                handleGetRequest(out, serverLamportClock);
            } else {
                sendResponse(out, "HTTP/1.1 400 Bad Request", "Invalid request method.", serverLamportClock);
            }

        } catch (IOException e) {
            System.out.println("Exception in handler: " + e.getMessage());
        }
    }

    private void handlePutRequest(BufferedReader in, Map<String, String> headers,
                                  PrintWriter out, int serverLamportClock) throws IOException {
        int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));

        // Read JSON data from request body
        char[] bodyChars = new char[contentLength];
        in.read(bodyChars);
        String body = new String(bodyChars);

        // Parse JSON data
        Map<String, String> weatherData = parseJson(body);

        // Update weather data map
        String stationId = weatherData.get("id");
        if (stationId != null) {
            WeatherStationData stationData = server.getWeatherDataMap()
                    .computeIfAbsent(stationId, k -> new WeatherStationData());

            synchronized (stationData) {
                stationData.updateData(weatherData);
                stationData.setLamportClock(serverLamportClock);
                stationData.setLastUpdateTime(System.currentTimeMillis());
            }

            sendResponse(out, "HTTP/1.1 200 OK", "Data updated successfully.", serverLamportClock);
        } else {
            sendResponse(out, "HTTP/1.1 400 Bad Request", "Station ID is missing.", serverLamportClock);
        }
    }

    private void handleGetRequest(PrintWriter out, int serverLamportClock) {
        // Aggregate weather data
        StringBuilder responseBody = new StringBuilder();
        responseBody.append("[");

        Iterator<WeatherStationData> iterator = server.getWeatherDataMap().values().iterator();
        while (iterator.hasNext()) {
            WeatherStationData data = iterator.next();
            synchronized (data) {
                responseBody.append(data.toJson());
                if (iterator.hasNext()) {
                    responseBody.append(",");
                }
            }
        }
        responseBody.append("]");

        // Send response
        sendResponse(out, "HTTP/1.1 200 OK", responseBody.toString(), serverLamportClock, "application/json");
    }

    private Map<String, String> parseJson(String json) {
        Map<String, String> dataMap = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("\"", "");
                    String value = kv[1].trim().replaceAll("\"", "");
                    dataMap.put(key, value);
                }
            }
        }
        return dataMap;
    }

    private void sendResponse(PrintWriter out, String statusLine, String body, int lamportClock) {
        sendResponse(out, statusLine, body, lamportClock, "text/plain");
    }

    private void sendResponse(PrintWriter out, String statusLine, String body, int lamportClock, String contentType) {
        out.println(statusLine);
        out.println("Content-Type: " + contentType);
        out.println("Lamport-Clock: " + lamportClock);
        out.println("Content-Length: " + body.getBytes().length);
        out.println();
        out.println(body);
    }
}

// Class to hold weather data for a station
class WeatherStationData {
    private Map<String, String> data = new HashMap<>();
    private int lamportClock;
    private long lastUpdateTime;
    private LinkedList<Map<String, String>> recentUpdates = new LinkedList<>();

    public synchronized void updateData(Map<String, String> newData) {
        data.putAll(newData);
        recentUpdates.add(new HashMap<>(newData));
        if (recentUpdates.size() > 20) {
            recentUpdates.removeFirst();
        }
    }

    public synchronized String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        Iterator<Map.Entry<String, String>> iterator = data.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            if (iterator.hasNext()) {
                json.append(",");
            }
        }
        json.append("}");
        return json.toString();
    }

    public int getLamportClock() {
        return lamportClock;
    }

    public void setLamportClock(int lamportClock) {
        this.lamportClock = lamportClock;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}
