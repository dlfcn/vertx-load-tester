# vertx-load-tester
Vertx Load Tester

//todo - dynamically set this (path of configuration files to tests).

To run load tester...
1. Build project
2. Go to /vertx-load-tester/target
3. Start server; $ java -jar vertx-load-tester-1.0.0-SNAPSHOT.jar server [/path/to/config/file]
4. Start client; $ java -jar vertx-load-tester-1.0.0-SNAPSHOT.jar client [/path/to/config/file]
5. Stop server/client; control + c

Json schema for client and server configuration...
{
  "title" : "vertx-load-tester.json",
  "description" : "Client and server configuration.",
  "type" : "object",
  "properties" : {
    "client" : {
      "description" : "Client configuration.",
      "type" : "object",
      "properties" : {
        "numberOfConnections" : {
          "description" : "Desired number of TCP connections. How many clients/threads do you want to start for sending requests?",
          "type" : "integer",
          "default" : 1
        },
        "tpsPerConnection" : {
          "description" : "Desired TPS per connection. How many requests do you want each client/thread to send per second over its TCP connection?",
          "type" : "integer",
          "default" : 100
        },
        "multiplexingLimit" : {
          "description" : "Multiplexing limit for each TCP connection. How many streams/transactions should one TCP connection support?",
          "type" : "integer",
          "default" : 1000
        },
        "httpMethod" : {
          "description" : "Method of the HTTP request.",
          "type" : "string",
          "enum" : [ "GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE", "PATCH" ]
        },
        "host" : {
          "description" : "Host of the HTTP request.",
          "type" : "string",
          "default" : "localhost"
        },
        "port" : {
          "description" : "Port of the HTTP reuqest.",
          "type" : "integer",
          "default" : 8080
        },
        "path" : {
          "description" : "Path of the HTTP request.",
          "type" : "string",
          "default" : "/"
        },
        "headers" : {
          "description" : "Headers of the HTTP request.",
          "type" : "array",
          "items" : {
            "type" : "object",
            "properties" : {
              "name" : {
                "description" : "Name of the header.",
                "type" : "string"
              },
              "value" : {
                "description" : "Value of the header.",
                "type" : "string"
              }
            },
            "required" : [ "name", "value" ]
          }
        },
        "body" : {
          "description" : "Body of the HTTP request.",
          "type" : "string"
        }
      }
    },
    "server" : {
      "description" : "Server configuration.",
      "type" : "object",
      "properties" : {
        "host" : {
          "description" : "Local interface to listen on.",
          "type" : "string",
          "default" : "localhost"
        },
        "port" : {
          "description" : "Local port to listen on.",
          "type" : "integer",
          "default" : 8080
        },
        "statusCode" : {
          "description" : "What status code should I respond with?",
          "type" : "integer",
          "default" : 200
        },
        "headers" : {
          "description" : "Headers of the HTTP response.",
          "type" : "array",
          "items" : {
            "type" : "object",
            "properties" : {
              "name" : {
                "description" : "Name of the header.",
                "type" : "string"
              },
              "value" : {
                "description" : "Value of the header.",
                "type" : "string"
              }
            },
            "required" : [ "name", "value" ]
          }
        },
        "body" : {
          "description" : "Body of the HTTP response.",
          "type" : "string"
        },
        "verticles" : {
          "description" : "Number of HTTP service verticles. Default is 2x cpu cores.",
          "type" : "integer"
        },
        "multiplexingLimit" : {
          "description" : "Multiplexing limit for each TCP connection. How many streams/transactions should one TCP connection support?",
          "type" : "integer",
          "default" : 1000
        },
        "blockingNanos" : {
          "description" : "Should be zero unless you want to simulate the duration it takes to execute service logic.",
          "type" : "integer",
          "default" : 0
        },
        "executeBlocking" : {
          "description" : "Should be false unless you want to offload request processing to worker thread instead of event loop thread.",
          "type" : "boolean",
          "default" : false
        }
      }
    }
  }
}
