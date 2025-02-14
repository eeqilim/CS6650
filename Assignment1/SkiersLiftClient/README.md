# Skiers Lift Client

## Requirements

Prerequisites:
- Java 17 or later
- Maven

Required Libraries:
- Apache HttpClient 5.4+
- Google Gson 2.8+
- JFreeChart

## How to Run

1. [Multithreaded Client](src/main/java/Main.java)

    This runs the full multithreaded client that generates and sends 200,000 requests to the server for load testing.

2. [Latency Test](src/main/java/SingleThreadLatency.java)
   
    This tests how long a single request takes by running 10,000 requests sequentially in a single thread.

## Configuration

1. **Server URL**
    
    Modify the SERVER_URL in both `Main.java` and `SingleThreadLatency.java` to point to your server endpoint:
    ```
    private static final String SERVER_URL = "your_server_path"
    ```
   
2. **CSV and Plot Output Paths** 

    Metrics will be saved in the specified CSV file and plot image file. Update these paths if needed:
    ```
   private static final String CSV_PATH = "src/main/java/Part2/result.csv";
   private static final String IMG_PATH = "src/main/java/Part2/plot.png";
   ```
   
3. **Thread and Request Settings**

    To control the load, adjust the number of threads and requests per thread in Main.java:
    ```
   private static final int TOTAL_REQUESTS = 200000;
   private static final int INITIAL_THREAD_COUNT = 32;
   private static final int SECOND_PHASE_THREAD_COUNT = 336;
   private static final int REQUESTS_PER_INITIAL_THREAD = 1000;
   private static final int REQUESTS_PER_SECOND_PHASE_THREAD = 500;
    ```