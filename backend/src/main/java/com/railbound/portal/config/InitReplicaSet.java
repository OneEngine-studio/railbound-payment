package com.railbound.portal.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.List;

public class InitReplicaSet {

    public static void main(String[] args) {
        System.out.println("Connecting directly to MongoDB on 127.0.0.1:27018...");
        try (MongoClient client = MongoClients.create("mongodb://127.0.0.1:27018/?directConnection=true")) {
            MongoDatabase admin = client.getDatabase("admin");
            try {
                Document status = admin.runCommand(new Document("replSetGetStatus", 1));
                System.out.println("Replica set is already initiated! State: " + status.get("myState"));
            } catch (Exception e) {
                System.out.println("Initiating replica set rs0...");
                Document config = new Document("_id", "rs0")
                        .append("members", List.of(new Document("_id", 0).append("host", "127.0.0.1:27018")));
                Document res = admin.runCommand(new Document("replSetInitiate", config));
                System.out.println("Initiate response: " + res.toJson());
                System.out.println("Replica set rs0 initiated successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
