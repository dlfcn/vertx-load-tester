# vertx-load-tester
Vertx Load Tester

To run load tester...

1. Build project
2. Go to /vertx-load-tester/target
3. Run jar like; $ java -jar vertx-load-tester-1.0.0-SNAPSHOT.jar 10 5000 1000000 POST localhost 8080 /nausf-auth/v1/ue-authentications/

Note that seven parameters in the following order are required to run the load tester...

1. Desired number of connection(s) 
2. Desired TPS per connection
3. Multiplexing limit for each connection 
4. HTTP method of the request 
5. Host of HTTP server being load tested 
6. Port of HTTP server being load tested 
7. Path of the HTTP service being load tested
