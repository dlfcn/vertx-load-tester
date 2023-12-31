/*
 * TestConfiguration.java
 *
 * Copyright (c) 2023 Titanium Software Holdings Inc. All Rights Reserved.
 *
 * This software is the proprietary information of Titanium Software Holdings Inc. 
 * Use is subject to license terms.
 *
 * @author Titanium Software Holdings Inc.
 */
package titanium.vertx.load.tester.config;

import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.JsonSchemaValidationException;
import io.vertx.json.schema.OutputUnit;
import io.vertx.json.schema.Validator;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Loads the configuration from file and creates a client or server
 * configuration instance.
 */
public class TestConfiguration {

    public static ClientConfiguration getClientConfiguration(String pathName) {

        JsonObject config = getConfiguration(pathName);
        
        if (config.containsKey("client")) {
            return new ClientConfiguration(config.getJsonObject("client"));
        } else {
            throw new IllegalArgumentException("Configuration does not contain client json object.");
        }
    }
    
    public static ServerConfiguration getServerConfiguration(String pathName) {

        JsonObject config = getConfiguration(pathName);

        if (config.containsKey("server")) {
            return new ServerConfiguration(config.getJsonObject("server"));
        } else {
            throw new IllegalArgumentException("Configuration does not contain server json object.");
        }
    }
    
    private static JsonObject getConfiguration(String pathName) {
        
        File file = new File(pathName);

        // verify file exists, that is a file, and it can be read
        if (!file.exists()) {
            throw new IllegalArgumentException(String.format("Configuration file [%s] does not exist.", pathName));
        } else if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("Configuration file [%s] is not a file.", pathName));
        } else if (!file.canRead()) {
            throw new IllegalArgumentException(String.format("Configuration file [%s] is not readable.", pathName));
        }

        // create scanner to read configuration file
        Scanner scanner;

        try {
            scanner = new Scanner(file);
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException(ex);
        }

        // create string builder and append configuration file to it
        StringBuilder builder = new StringBuilder();

        while (scanner.hasNextLine()) {
            builder.append(scanner.nextLine());
        }

        // create json object using configuration fle that was scanned
        JsonObject config = new JsonObject(builder.toString());

        // validate configuration json object against schema definition
        OutputUnit outputUnit = validate(config);

        try {
            outputUnit.checkValidity();
        } catch (JsonSchemaValidationException ex) {
            throw new IllegalArgumentException(ex);
        }
        
        return config;
    }

    private static final String JSON_SCHEMA_STRING = ""
            + "{"
            + "    \"title\": \"vertx-load-tester.json\","
            + "    \"description\": \"Client and server configuration.\","
            + "    \"type\": \"object\","
            + "    \"properties\": {"
            + "        \"client\": {"
            + "            \"description\": \"Client configuration.\","
            + "            \"type\": \"object\","
            + "            \"properties\": {"
            + "                \"numberOfClients\": {"
            + "                    \"description\": \"Number of clients. How many clients/threads do you want to start sending requests?\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 1"
            + "                },"
            + "                \"numberOfConnections\": {"
            + "                    \"description\": \"Number of connections. How many connections should each client/thread get?\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 1"
            + "                },"
            + "                \"multiplexingLimit\": {"
            + "                    \"description\": \"Multiplexing limit. How many streams/transactions should each connection support?\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 1000"
            + "                },"
            + "                \"httpMethod\": {"
            + "                    \"description\": \"Method of the HTTP request.\","
            + "                    \"type\": \"string\","
            + "                    \"enum\": ["
            + "                        \"GET\","
            + "                        \"HEAD\","
            + "                        \"POST\","
            + "                        \"PUT\","
            + "                        \"DELETE\","
            + "                        \"CONNECT\","
            + "                        \"OPTIONS\","
            + "                        \"TRACE\","
            + "                        \"PATCH\""
            + "                    ]"
            + "                },"
            + "                \"host\": {"
            + "                    \"description\": \"Host of the HTTP request.\","
            + "                    \"type\": \"string\","
            + "                    \"default\": \"localhost\""
            + "                },"
            + "                \"port\": {"
            + "                    \"description\": \"Port of the HTTP reuqest.\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 8080"
            + "                },"
            + "                \"path\": {"
            + "                    \"description\": \"Path of the HTTP request.\","
            + "                    \"type\": \"string\","
            + "                    \"default\": \"/\""
            + "                },"
            + "                \"headers\": {"
            + "                    \"description\": \"Headers of the HTTP request.\","
            + "                    \"type\": \"array\","
            + "                    \"items\": {"
            + "                        \"type\": \"object\","
            + "                        \"properties\": {"
            + "                            \"name\": {"
            + "                                \"description\": \"Name of the header.\","
            + "                                \"type\": \"string\""
            + "                            },"
            + "                            \"value\": {"
            + "                                \"description\": \"Value of the header.\","
            + "                                \"type\": \"string\""
            + "                            }"
            + "                        },"
            + "                        \"required\": ["
            + "                            \"name\","
            + "                            \"value\""
            + "                        ]"
            + "                    }"
            + "                },"
            + "                \"body\": {"
            + "                    \"description\": \"Body of the HTTP request.\","
            + "                    \"type\": \"string\""
            + "                },"
            + "                \"expectedStatusCode\": {"
            + "                    \"description\": \"What status code should I expect in the response?\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 200"
            + "                }"
            + "            }"
            + "        },"
            + "        \"server\": {"
            + "            \"description\": \"Server configuration.\","
            + "            \"type\": \"object\","
            + "            \"properties\": {"
            + "                \"host\": {"
            + "                    \"description\": \"Local interface to listen on.\","
            + "                    \"type\": \"string\","
            + "                    \"default\": \"localhost\""
            + "                },"
            + "                \"port\": {"
            + "                    \"description\": \"Local port to listen on.\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 8080"
            + "                },"
            + "                \"statusCode\": {"
            + "                    \"description\": \"What status code should I respond with?\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 200"
            + "                },"
            + "                \"headers\": {"
            + "                    \"description\": \"Headers of the HTTP response.\","
            + "                    \"type\": \"array\","
            + "                    \"items\": {"
            + "                        \"type\": \"object\","
            + "                        \"properties\": {"
            + "                            \"name\": {"
            + "                                \"description\": \"Name of the header.\","
            + "                                \"type\": \"string\""
            + "                            },"
            + "                            \"value\": {"
            + "                                \"description\": \"Value of the header.\","
            + "                                \"type\": \"string\""
            + "                            }"
            + "                        },"
            + "                        \"required\": ["
            + "                            \"name\","
            + "                            \"value\""
            + "                        ]"
            + "                    }"
            + "                },"
            + "                \"body\": {"
            + "                    \"description\": \"Body of the HTTP response.\","
            + "                    \"type\": \"string\""
            + "                },"
            + "                \"multiplexingLimit\": {"
            + "                    \"description\": \"Multiplexing limit. How many streams/transactions should each connection support?\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 1000"
            + "                },"
            + "                \"blockingMillis\": {"
            + "                    \"description\": \"Should be zero unless you want to simulate the duration it takes to execute service logic.\","
            + "                    \"type\": \"integer\","
            + "                    \"default\": 0"
            + "                },"
            + "                \"executeBlocking\": {"
            + "                    \"description\": \"Should be false unless you want to offload request processing to worker-thread instead of event-loop-thread.\","
            + "                    \"type\": \"boolean\","
            + "                    \"default\": false"
            + "                }"
            + "            }"
            + "        }"
            + "    }"
            + "}";

    private static final JsonObject JSON_OBJECT = new JsonObject(JSON_SCHEMA_STRING);
    private static final JsonSchema JSON_SCHEMA = JsonSchema.of(JSON_OBJECT);
    private static final JsonSchemaOptions JSON_SCHEMA_OPTIONS = new JsonSchemaOptions().setDraft(Draft.DRAFT7).setBaseUri("https://vertx.io");
    private static final Validator JSON_VALIDATOR = Validator.create(JSON_SCHEMA, JSON_SCHEMA_OPTIONS);

    public static OutputUnit validate(JsonObject json) {
        return JSON_VALIDATOR.validate(json);
    }
}
