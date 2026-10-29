package com.railbound.portal.config;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.List;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @PostConstruct
    public void ensureReplicaSetInitiated() {
        try (MongoClient directClient = MongoClients.create("mongodb://127.0.0.1:27018/?directConnection=true")) {
            MongoDatabase adminDb = directClient.getDatabase("admin");
            try {
                Document status = adminDb.runCommand(new Document("replSetGetStatus", 1));
                log.info("MongoDB replica set 'rs0' is active. MyState: {}", status.get("myState"));
            } catch (MongoCommandException e) {
                if (e.getErrorCode() == 94 || e.getMessage().toLowerCase().contains("no replset config")) {
                    log.info("Initiating single-node replica set 'rs0' on 127.0.0.1:27018...");
                    Document config = new Document("_id", "rs0")
                            .append("members", List.of(new Document("_id", 0).append("host", "127.0.0.1:27018")));
                    adminDb.runCommand(new Document("replSetInitiate", config));
                    log.info("Successfully initiated replica set 'rs0'. Waiting for primary election...");
                    Thread.sleep(3000);
                } else {
                    log.warn("Replica set check warning: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not auto-verify replica set initiation: {}", e.getMessage());
        }
    }

    /**
     * Enables Spring's transactional management backed by MongoDB multi-document ACID transactions.
     * Requires MongoDB to be running as a replica set (single node 'rs0' for local development).
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
