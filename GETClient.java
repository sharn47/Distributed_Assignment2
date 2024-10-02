import java.io.*;
import java.net.*;
import java.util.*;

/**
 * GETClient is a simple HTTP client that connects to a specified server,
 * sends an HTTP GET request (optionally with a station ID as a query parameter),
 * handles Lamport clock synchronization for event ordering in distributed systems,
 * and parses and displays the JSON response from the server.
 */
public class GETClient {

    // Initialize the Lamport clock to 0 for this client instance
    private static int lamportClock = 0;

    public static void main(String[] args) {
        // Check if at least one argument is provided (the server URL)
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server-url> [station-id]");
            return;
        }

        // Retrieve the server URL and optional station ID from the command-line arguments
        String serverUrl = args[0];  // The first argument is the server URL
        String stationId = args.length > 1 ? args[1] : null;  // The second argument is the optional station ID

        try {
            // Parse the server URL into a URL object to extract components like host and port
            URL url = new URL(serverUrl);

            // Establish a TCP socket connection to the server using the host and port from the URL
            // If the port is not specified in the URL (url.getPort() returns -1), default to port 80 (standard HTTP port)
            Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());

            // Set up output stream to send data to the server
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            // Set up input stream to receive data from the server
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Build the path for the GET request starting with the path component of the URL
            String path = url.getPath();
            // If a station ID is provided, append it as a query parameter to the path
            if (stationId != null) {
                // Check if the path already contains a query parameter
                if (path.contains("?")) {
                    // Append the station ID with an '&' if other query parameters exist
                    path += "&id=" + stationId;
                } else {
                    // Append the station ID starting with a '?'
                    path += "?id=" + stationId;
                }
            }

            // **Lamport Clock Increment**
            // Increment the Lamport clock before sending the request to represent a local event
            lamportClock++;

            // Send the HTTP GET request with necessary headers to the server
            out.println("GET " + path + " HTTP/1.1");          // Request line specifying the method, path, and HTTP version
            out.println("Host: " + url.getHost());             // Host header indicating the server's hostname
            out.println("Lamport-Clock: " + lamportClock);     // Custom header to send the current Lamport clock value
            out.println("Connection: close");                  // Header to indicate that the connection should be closed after the response
            out.println();                                     // Blank line to signal the end of the request headers

            // Read the status line from the server's response (e.g., "HTTP/1.1 200 OK")
            String statusLine = in.readLine();
            if (statusLine == null) {
                // If no response is received from the server, print an error message and exit
                System.out.println("No response from server.");
                return;
            }

            // Read the response headers from the server and store them in a map for easy access
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            // Continue reading header lines until an empty line is encountered (which indicates the end of headers)
            while (!(headerLine = in.readLine()).equals("")) {
                // Split each header line into a key and value pair at the first occurrence of ": "
                String[] headerParts = headerLine.split(": ", 2);
                if (headerParts.length == 2) {
                    // Store the header name and value in the headers map
                    headers.put(headerParts[0], headerParts[1]);
                }
            }

            // **Update Lamport Clock based on server's Lamport clock**
            // Retrieve the Lamport clock value sent by the server from the response headers
            int serverLamportClock = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            // Update the client's Lamport clock to be one greater than the maximum of its own clock and the server's clock
            lamportClock = Math.max(lamportClock, serverLamportClock) + 1;

            // Read the response body from the server
            StringBuilder responseBody = new StringBuilder();
            String line;
            // Read each line of the response body until the end of the stream
            while ((line = in.readLine()) != null) {
                // Append the line to the responseBody StringBuilder
                responseBody.append(line);
            }

            // **Parse and display the JSON response**
            // Call the method to parse the JSON response and display it in a formatted manner
            parseAndDisplayJson(responseBody.toString());

            // Close the socket connection to release resources
            socket.close();

        } catch (Exception e) {
            // If an exception occurs, print an error message with the exception details
            System.out.println("Client exception: " + e.getMessage());
        }
    }

    /**
     * Parses a JSON-formatted string and displays its content in a readable format.
     * This method assumes that the JSON response is either a JSON array of objects or a single JSON object.
     *
     * @param json The JSON string to parse and display.
     */
    private static void parseAndDisplayJson(String json) {
        // **Method to parse and display JSON response**

        // Trim any leading and trailing whitespace from the JSON string
        json = json.trim();
        // Check if the JSON string starts with a '[' character, indicating a JSON array
        if (json.startsWith("[")) {
            // Remove the opening '[' and closing ']' to simplify parsing individual objects
            json = json.substring(1, json.length() - 1);
        }

        // Split the JSON string into individual JSON objects
        // The regex "\\},\\{" matches the '},{' pattern that separates JSON objects in an array
        String[] objects = json.split("\\},\\{");
        // Iterate over each JSON object in the array
        for (String obj : objects) {
            // Remove any remaining curly braces '{' or '}' from the beginning and end of the object
            obj = obj.replaceAll("\\{|\\}", "");
            // Split the object into key-value pairs separated by commas
            String[] pairs = obj.split(",");
            // Iterate over each key-value pair in the object
            for (String pair : pairs) {
                // Split the pair into key and value at the first occurrence of ':' (limit to 2 parts)
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    // Extract the key by trimming whitespace and removing any double quotes
                    String key = kv[0].trim().replaceAll("\"", "");
                    // Extract the value by trimming whitespace and removing any double quotes
                    String value = kv[1].trim().replaceAll("\"", "");
                    // Display the key and value in the format "key: value"
                    System.out.println(key + ": " + value);
                }
            }
            // Print a separator line after each object for readability
            System.out.println("-------------------------");
        }
    }
}
