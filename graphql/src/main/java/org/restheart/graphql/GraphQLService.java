package org.restheart.graphql;

import com.google.gson.Gson;
import com.mongodb.MongoClient;
import graphql.ExecutionInput;
import graphql.GraphQL;
import io.undertow.server.HttpServerExchange;
import org.restheart.ConfigurationException;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.cache.AppDefinitionLoader;
import org.restheart.graphql.cache.AppDefinitionLoadingCache;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.exchange.GraphQLRequest;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.scalars.bsonCoercing.CoercingUtils;
import org.restheart.plugins.*;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;


@RegisterPlugin(name= "graphql",
                description = "Service that handles GraphQL requests",
                enabledByDefault = true,
                defaultURI = "/graphql")

public class GraphQLService implements Service<GraphQLRequest, MongoResponse> {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(GraphQLService.class);

    private GraphQL gql;
    private MongoClient mongoClient = null;
    private String db = null;
    private String collection = null;

    @InjectConfiguration
    public void initConf(Map<String, Object> args) throws ConfigurationException, NoSuchFieldException, IllegalAccessException {
        CoercingUtils.replaceBuiltInCoercing();
        this.db = ConfigurablePlugin.argValue(args, "db");
        this.collection = ConfigurablePlugin.argValue(args, "collection");

        if(mongoClient != null){
            GraphQLDataFetcher.setMongoClient(mongoClient);
            AppDefinitionLoader.setup(db, collection, mongoClient);
        }
    }

    @InjectMongoClient
    public void initMongoClient(MongoClient mClient){
        this.mongoClient = mClient;
        if (db!= null && collection != null){
            GraphQLDataFetcher.setMongoClient(mongoClient);
            AppDefinitionLoader.setup(db, collection, mongoClient);
        }
    }


    @Override
    public void handle(GraphQLRequest request, MongoResponse response) throws Exception {

        GraphQLApp graphQLApp = request.getAppDefinition();
        ExecutionInput.Builder inputBuilder = ExecutionInput.newExecutionInput().query(request.getQuery());
        inputBuilder.operationName(request.getOperationName());
        if (request.hasVariables()){
            inputBuilder.variables((new Gson()).fromJson(request.getVariables(), Map.class));
        }

        this.gql = GraphQL.newGraphQL(graphQLApp.getExecutableSchema()).build();

        var result = this.gql.execute(inputBuilder.build());

        if (!result.getErrors().isEmpty()){
            response.setInError(400, "Bad Request");
        }
        response.setContent(JsonUtils.toBsonDocument(result.toSpecification()));
    }

    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> {

            try{
                AppDefinitionLoadingCache cache = AppDefinitionLoadingCache.getInstance();
                String[] splitPath = e.getRequestPath().split("/");
                String appUri = String.join("/", Arrays.copyOfRange(splitPath, 2, splitPath.length));
                GraphQLApp appDef = cache.get(appUri);
                GraphQLRequest.init(e, appUri, appDef);
            }catch (GraphQLAppDefNotFoundException notFoundException){
                LOGGER.error(notFoundException.getMessage());
                throw new BadRequestException(HttpStatus.SC_NOT_FOUND);
            }
        };
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> MongoResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, GraphQLRequest> request() {
        return e -> GraphQLRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, MongoResponse> response() {
        return e -> MongoResponse.of(e);
    }
}
