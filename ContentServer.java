import java.io.*;
import java.net.*;
import java.util.*;

public class ContentServer {

    // Static variable to keep track of the Lamport clock for synchronization
    private static int lamportClock = 0;

    public static void main(String[] args) {
        // Check if the required arguments are provided (server URL and file path)
        if (args.length < 2) {
            System.out.println("Usage: java ContentServer <server-url> <file-path>");
            return; // Exit if arguments are insufficient
        }

        // Assign the first argument as the server URL
        String serverUrl = args[0];
        // Assign the second argument as the file path
        String filePath = args[1];

        try {
            // Read weather data from the specified file
            Map<String, String> weatherData = readDataFromFile(filePath);
            if (weatherData == null) {
                System.out.println("Failed to read data from file.");
                return; // Exit if reading data fails
            }

            // Convert the weather data map to a JSON-formatted string
            String jsonData = convertToJson(weatherData);

            // Send an HTTP PUT request to the server with the JSON data
            sendPutRequest(serverUrl, jsonData);

        } catch (Exception e) {
            // Handle any exceptions that occur during execution
            System.out.println("ContentServer exception: " + e.getMessage());
        }
    }

    /**
     * Reads key-value pairs from a file and stores them in a Map.
     *
     * @param filePath The path to the file containing the weather data.
     * @return A Map containing the key-value pairs from the file, or null if an error occurs.
     */
    private static Map<String, String> readDataFromFile(String filePath) {
        // Create a new HashMap to store the data
        Map<String, String> dataMap = new HashMap<>();
        // Use try-with-resources to ensure the BufferedReader is closed after use
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            // Read each line from the file until the end
            while ((line = br.readLine()) != null) {
                // Split the line into key and value based on the first colon found
                String[] kv = line.split(":", 2);
                if (kv.length == 2) {
                    // Trim whitespace and add the key-value pair to the map
                    dataMap.put(kv[0].trim(), kv[1].trim());
                }
            }
            // Return the populated map
            return dataMap;
        } catch (IOException e) {
            // Print an error message if reading the file fails
            System.out.println("Error reading file: " + e.getMessage());
            return null; // Return null to indicate failure
        }
    }

    /**
     * Converts a Map of key-value pairs to a JSON-formatted string.
     *
     * @param dataMap The Map containing data to be converted.
     * @return A JSON-formatted string representing the data.
     */
    private static String convertToJson(Map<String, String> dataMap) {
        // Use a StringBuilder for efficient string concatenation
        StringBuilder json = new StringBuilder();
        json.append("{"); // Start of JSON object
        // Get an iterator over the map's entry set
        Iterator<Map.Entry<String, String>> iterator = dataMap.entrySet().iterator();
        // Iterate over each key-value pair in the map
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            // Append the key and value in JSON format
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            if (iterator.hasNext()) {
                json.append(","); // Add a comma if there are more entries
            }
        }
        json.append("}"); // End of JSON object
        // Return the JSON string
        return json.toString();
    }

    /**
     * Sends an HTTP PUT request to the specified server URL with the provided JSON data.
     * Also handles Lamport clock synchronization based on the server's response.
     *
     * @param serverUrl The URL of the server to send the request to.
     * @param jsonData  The JSON-formatted data to be sent in the request body.
     */
    private static void sendPutRequest(String serverUrl, String jsonData) {
        try {
            // Create a URL object from the server URL string
            URL url = new URL(serverUrl);
            // Establish a socket connection to the server's host and port
            Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());

            // Create a PrintWriter to send data to the server
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            // Create a BufferedReader to read the server's response
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Increment the Lamport clock before sending the request to represent an event
            lamportClock++;

            // Construct and send the HTTP PUT request headers
            out.println("PUT " + url.getPath() + " HTTP/1.1"); // Request line
            out.println("Host: " + url.getHost()); // Host header
            out.println("Content-Type: application/json"); // Content-Type header
            out.println("Content-Length: " + jsonData.getBytes().length); // Content-Length header
            out.println("Lamport-Clock: " + lamportClock); // Custom Lamport clock header
            out.println("Connection: close"); // Connection header to close the connection after response
            out.println(); // Blank line to indicate the end of headers
            out.println(jsonData); // Request body containing the JSON data

            // Read the response status line from the server
            String statusLine = in.readLine();
            if (statusLine == null) {
                System.out.println("No response from server.");
                return; // Exit if there's no response
            }

            // Read the response headers and store them in a map
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            // Continue reading headers until an empty line is encountered
            while (!(headerLine = in.readLine()).equals("")) {
                // Split the header line into name and value
                String[] headerParts = headerLine.split(": ");
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]); // Store the header
                }
            }

            // Extract the server's Lamport clock from the headers, defaulting to 0 if not present
            int serverLamportClock = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            // Update the client's Lamport clock using the maximum of the two clocks plus one
            lamportClock = Math.max(lamportClock, serverLamportClock) + 1;

            // Read the response body from the server
            StringBuilder responseBody = new StringBuilder();
            String line;
            // Read each line until the end of the stream
            while ((line = in.readLine()) != null) {
                responseBody.append(line); // Append each line to the response body
            }

            // Output the server's response body
            System.out.println("Server response: " + responseBody.toString());

            // Close the socket connection to free resources
            socket.close();

        } catch (Exception e) {
            // Handle any exceptions that occur during the request
            System.out.println("Error in sendPutRequest: " + e.getMessage());
        }
    }
}
