# vertx-load-tester
Vertx Load Tester

To run load tester...
1. Build project
2. Go to /vertx-load-tester/target
3. Start server; $ java -jar vertx-load-tester-1.0.0-SNAPSHOT.jar server [port] [multiplexingLimit] [blockingNanos] [executeBlocking]
4. Start client; $ java -jar vertx-load-tester-1.0.0-SNAPSHOT.jar client [connections] [tps] [multiplexingLimit] [httpMethod] [host] [port] [path]

Server arguments...
1. Must be "server"
2. Multiplexing limit for each TCP connection 
-- How many streams/requests/transactions should one TCP connection support? 1k-10k should do it.
3. Determines if server will simulate service logic by looping.
-- Should be zero, unless you want to simulate service logic.
4. Determines if server will execute service logic using a worker verticle thread.
-- Should be false, unless you want to offload processing to worker thread.

Client arguments...
1. Must be "client"
2. Desired number of TCP connection(s)
-- How meany clients/threads do you want me to start for sending requests?
3. Desired TPS per connection
-- How many requests do you want each client/thread to send per second over its TCP connection?
4. Multiplexing limit for each TCP connection 
-- How many streams/requests/transactions should one TCP connection support? 1k-10k should do it.
5. HTTP method of the request 
6. Host of HTTP server being load tested 
7. Port of HTTP server being load tested 
8. Path of the HTTP service being load tested

//todo - update server arguments so that you can pass in the response the server should send.
