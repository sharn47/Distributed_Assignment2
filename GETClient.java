import java.io.*;
import java.net.*;
import java.util.*;

public class GETClient {

    private static int lamportClock = 0;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server-url> [station-id]");
            return;
        }

        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;

        try {
            // Parse the server URL
            URL url = new URL(serverUrl);

            // Establish a socket connection to the server
            Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());

            // Set up input and output streams
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Build the GET request path
            String path = url.getPath();
            if (stationId != null) {
                path += "?id=" + stationId;
            }

            // **Lamport Clock Increment**
            lamportClock++;

            // Send the GET request with Lamport clock
            out.println("GET " + path + " HTTP/1.1");
            out.println("Host: " + url.getHost());
            out.println("Lamport-Clock: " + lamportClock);
            out.println("Connection: close");
            out.println();

            // Read the status line
            String statusLine = in.readLine();
            if (statusLine == null) {
                System.out.println("No response from server.");
                return;
            }

            // Read the headers and store them in a map
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while (!(headerLine = in.readLine()).equals("")) {
                String[] headerParts = headerLine.split(": ");
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]);
                }
            }

            // **Update Lamport Clock based on server's Lamport clock**
            int serverLamportClock = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            lamportClock = Math.max(lamportClock, serverLamportClock) + 1;

            // Read the response body
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                responseBody.append(line);
            }

            // **Parse and display the JSON response**
            parseAndDisplayJson(responseBody.toString());

            // Close the socket
            socket.close();

        } catch (Exception e) {
            System.out.println("Client exception: " + e.getMessage());
        }
    }

    // **Method to parse and display JSON response**
    private static void parseAndDisplayJson(String json) {
        // Trim and clean the JSON string
        json = json.trim();
        if (json.startsWith("[")) {
            json = json.substring(1, json.length() - 1);
        }

        // Split the JSON array into individual objects
        String[] objects = json.split("\\},\\{");
        for (String obj : objects) {
            obj = obj.replaceAll("\\{|\\}", "");
            String[] pairs = obj.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("\"", "");
                    String value = kv[1].trim().replaceAll("\"", "");
                    System.out.println(key + ": " + value);
                }
            }
            System.out.println("-------------------------");
        }
    }
}
