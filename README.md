# Distributed_Assignment2
Sharan Nixon (a1944177)

## Content Server
Summary of ContentServer

The ContentServer is a Java application designed to read weather data from a file, convert it into JSON format, and send it to a specified server using an HTTP PUT request. The application incorporates Lamport clock synchronization to handle event ordering in distributed systems, ensuring consistency across multiple processes.

## Key Functionalities:

Reading Data from File:

The application reads key-value pairs from a specified file, where each line is expected to be in the format key: value.
It stores this data in a Map<String, String> for easy manipulation and access.

Converting Data to JSON:

The collected data is converted into a JSON-formatted string.
In the optimized version, a JSON library like org.json is used for accurate and efficient serialization, handling special characters and data types properly.
Sending HTTP PUT Request:

The application sends the JSON data to a specified server URL using an HTTP PUT request.
The optimized code utilizes Java's HttpURLConnection for handling HTTP communications, simplifying the process and improving code maintainability.

Lamport Clock Synchronization:

The application maintains a Lamport clock (lamportClock) to synchronize events in a distributed environment.
Before sending a request, it increments the Lamport clock and includes its value in the request header (Lamport-Clock).
Upon receiving a response, it updates the Lamport clock based on the server's Lamport clock value to maintain a consistent event order.

## Aggregation Server
The Aggregation Server is a Java-based application designed to collect, aggregate, and manage weather data from multiple weather stations. It operates as a multithreaded server capable of handling concurrent client connections and requests. The server supports basic HTTP methods (PUT and GET) to facilitate data collection and retrieval, and incorporates advanced features like Lamport clock synchronization and data expiration mechanisms to maintain data consistency and freshness.

## Key Features:

Concurrent Client Handling:

Utilizes multithreading to manage multiple client connections simultaneously.
Each client connection is handled in a separate ServerHandler thread, ensuring efficient processing without blocking other clients.

Weather Data Storage:

Stores weather data in a thread-safe ConcurrentHashMap, keyed by the weather station's unique id.
Each weather station's data is encapsulated in a WeatherStationData object, which maintains the latest data and a history of recent updates.
HTTP Request Handling:

PUT Requests:

Weather stations send their data to the server using PUT requests with JSON-formatted bodies.
The server parses the JSON data, updates the corresponding WeatherStationData object, and responds with a success message.

GET Requests:

Clients retrieve aggregated weather data using GET requests.
The server compiles data from all active weather stations into a JSON array and sends it back to the client.

Lamport Clock Synchronization:

Implements a Lamport clock (AtomicInteger) to maintain a logical sequence of events in a distributed system.
When processing requests, the server updates its Lamport clock based on the clock value received from clients in the Lamport-Clock header.
The updated Lamport clock value is included in the response headers, ensuring clients and the server are synchronized in terms of event ordering.

Data Expiration Mechanism:

Includes a scheduled task that runs every 30 seconds to remove outdated weather data that hasn't been updated within the last 30 seconds.
This mechanism ensures that the server only retains fresh and relevant weather information, preventing stale data from affecting aggregated results.

## GET Client

The GETClient program is a Java application that functions as a simple HTTP client. It connects to a specified server, sends an HTTP GET request (optionally with a query parameter), and processes the server's response. The program also implements Lamport clock synchronization to manage event ordering in distributed systems and includes basic JSON parsing to display the server's response in a readable format.

## Key Features:

Command-Line Arguments:

<server-url>: The URL of the server to connect to.
[station-id] (optional): An optional station ID to include as a query parameter.

HTTP GET Request:

Constructs an HTTP GET request to the specified server.
Appends the station ID as a query parameter if provided (?id=stationId).
Sends the request over a TCP socket connection.

Lamport Clock Synchronization:

Maintains a Lamport clock (lamportClock) to synchronize events in a distributed environment.
Increments the clock before sending the request.
Includes the Lamport clock value in the request headers (Lamport-Clock).
Updates the local Lamport clock based on the maximum value between the local and the server's Lamport clock received in the response headers.


# Running the Project using Command Line

Directory Structure :: 
src/main/com/weather/app/

## Building the Project
1.	Navigate to the root directory of the project.
2.	Compile the Java files:

`javac -d bin src/main/com/weather/app/*.java`

## Running the Servers and Clients
1. Start the Aggregation Server
- Open a terminal and run the following command to start the Aggregation Server:

`java -cp bin com.weather.app.AggregationServer <server-address:port>`

Usage:
`java -cp bin com.weather.app.AggregationServer`

2. Start the Content Server
- In a new terminal, run the following command to start the Content Server. Replace <server-address:port> with the address and port of the Aggregation Server, and provide the path to the weather data file.

`java -cp bin com.weather.app.ContentServer <server-address:port> <file-path>`

Usage:
`java -cp bin com.weather.app.ContentServer localhost:4567 txt.txt`

3. Run the GET Client
- In another terminal, run the GET Client with the server address and port. Optionally, you can specify a station ID.

`java -cp bin com.weather.app.GETClient <server-address:port> [station-id]`

Usage:
`java -cp bin com.weather.app.GETClient localhost:4567`

------------------------------------------------------------------------------------------------------------------------------------------------
# Running the Project in Eclipse
## Prerequisites
Before running the project, ensure that the following requirements are met:
-•	Java Development Kit (JDK) installed on your machine.
-•	JSON library (e.g., Gson) placed in a lib/ folder within your project directory.

## Importing the Project in Eclipse
To import the project into Eclipse:
1.	Open Eclipse.
2.	Select File > Import.
3.	Choose Existing Projects into Workspace and click Next.
4.	Browse to the location of your project directory and select it.
5.	Click Finish to import the project into the workspace.
Compiling and Running the Programs

## Aggregation Server
1. Open the AggregationServer.java file in Eclipse.
2. Right-click on the file in the Package Explorer.
3. Select Run As > Java Application.
4. The default port for the Aggregation Server is set to 4567. If needed, you can modify this by passing a different port number in the Run Configurations:
-   Click on Run > Run Configurations....
-   Select Java Application for your AggregationServer.
-   If required, in the Arguments tab, provide the port number (e.g., 4567).
-   Click Apply and then Run.
-   
# References

- ChatGPT for understanding how GET and PUT requests work, converting text to json, how to run code on Maven Eclipse, understanding lamport clocks, general guidance on implementing and applying code solutions, refining the comments in the code.

- Lamport, Leslie. "Time, Clocks, and the Ordering of Events in a Distributed System." Massachusetts Computer Associates, Inc.
  
- Stack Overflow: Solutions and code snippets to errors encountered during implementations found from discussions and examples available on Stack Overflow.

- Github : References were taken from certain Github repositories to help in getting ideas on how to implement the solutions.

