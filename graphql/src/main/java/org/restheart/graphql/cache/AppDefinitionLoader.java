package org.restheart.graphql.cache;

import com.mongodb.MongoClient;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.graphql.GraphQLAppDeserializer;
import org.restheart.graphql.models.*;


public class AppDefinitionLoader {

    private static final String APP_URI_FIELD = "descriptor.uri";
    private static final String APP_ENABLED_FIELD = "descriptor.enabled";

    private static MongoClient mongoClient;
    private static String appDB;
    private static String appCollection;

    public static void setup(String _db, String _collection, MongoClient mclient){
        appDB = _db;
        appCollection = _collection;
        mongoClient = mclient;
    }

    public static GraphQLApp loadAppDefinition(String appURI){

        BsonArray conditions = new BsonArray();
        conditions.add(new BsonDocument(APP_URI_FIELD, new BsonString(appURI)));
        conditions.add(new BsonDocument(APP_ENABLED_FIELD, new BsonBoolean(true)));
        BsonDocument findArg = new BsonDocument("$and",conditions);

        BsonDocument appDefinition = mongoClient.getDatabase(appDB).getCollection(appCollection, BsonDocument.class)
                .find(findArg).first();

        if (appDefinition != null) {
            return GraphQLAppDeserializer.fromBsonDocument(appDefinition);
        } else {
            return null;
        }
    }


}
