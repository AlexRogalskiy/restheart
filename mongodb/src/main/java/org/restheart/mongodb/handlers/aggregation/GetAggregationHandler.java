/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.handlers.aggregation;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MapReduceIterable;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.FileRealmAccount;
import org.restheart.security.JwtAccount;
import org.restheart.security.MongoPermissions;
import org.restheart.security.MongoRealmAccount;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetAggregationHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetAggregationHandler.class);

    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    /**
     * Default ctor
     */
    public GetAggregationHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetAggregationHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        var queryUri = request.getAggregationOperation();

        var aggregations = AbstractAggregationOperation.getFromJson(request.getCollectionProps());

        var _query = aggregations.stream().filter(q -> q.getUri().equals(queryUri)).findFirst();

        if (!_query.isPresent()) {
            response.setInError(HttpStatus.SC_NOT_FOUND, "query does not exist");
            next(exchange);
            return;
        }

        var _data = new ArrayList<BsonDocument>();
        var query = _query.get();

        if (null == query.getType()) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown query type");
            next(exchange);
            return;
        } else {
            var avars = request.getAggreationVars() == null
                ? new BsonDocument()
                : request.getAggreationVars();

            // add the default variables to the avars document
            injectAvars(request, avars);

            switch (query.getType()) {
                case MAP_REDUCE:
                    MapReduceIterable<BsonDocument> mrOutput;
                    var mapReduce = (MapReduce) query;
                    try {
                        var clientSession = request.getClientSession();

                        if (clientSession == null) {
                            mrOutput = dbsDAO.getCollection(request.getDBName(), request.getCollectionName())
                                .mapReduce(mapReduce.getResolvedMap(avars), mapReduce.getResolvedReduce(avars))
                                .filter(mapReduce.getResolvedQuery(avars))
                                .maxTime(MongoServiceConfiguration.get() .getAggregationTimeLimit(), TimeUnit.MILLISECONDS);
                        } else {
                            mrOutput = dbsDAO.getCollection(request.getDBName(), request.getCollectionName())
                                .mapReduce(clientSession, mapReduce.getResolvedMap(avars), mapReduce.getResolvedReduce(avars))
                                .filter(mapReduce.getResolvedQuery(avars))
                                .maxTime(MongoServiceConfiguration.get() .getAggregationTimeLimit(), TimeUnit.MILLISECONDS);
                        }
                    } catch (MongoCommandException | InvalidMetadataException ex) {
                        response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error executing mapReduce", ex);
                        next(exchange);
                        return;
                    } catch (QueryVariableNotBoundException qvnbe) {
                        response.setInError(HttpStatus.SC_BAD_REQUEST, "error executing mapReduce: " + qvnbe.getMessage());
                        next(exchange);
                        return;
                    }
                    // ***** get data
                    for (BsonDocument obj : mrOutput) {
                        _data.add(obj);
                    }
                    break;
                case AGGREGATION_PIPELINE:
                    AggregateIterable<BsonDocument> agrOutput;
                    var pipeline = (AggregationPipeline) query;
                    try {
                        var clientSession = request.getClientSession();

                        if (clientSession == null) {
                            agrOutput = dbsDAO.getCollection(request.getDBName(), request.getCollectionName())
                                .aggregate(pipeline.getResolvedStagesAsList(avars))
                                .maxTime(MongoServiceConfiguration.get() .getAggregationTimeLimit(), TimeUnit.MILLISECONDS)
                                .allowDiskUse(pipeline.getAllowDiskUse().getValue());
                        } else {
                            agrOutput = dbsDAO.getCollection(request.getDBName(), request.getCollectionName())
                                .aggregate(clientSession, pipeline.getResolvedStagesAsList(avars))
                                .maxTime(MongoServiceConfiguration.get() .getAggregationTimeLimit(), TimeUnit.MILLISECONDS)
                                .allowDiskUse(pipeline.getAllowDiskUse().getValue());
                        }
                    } catch (MongoCommandException | InvalidMetadataException ex) {
                        response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error executing aggreation pipeline", ex);
                        next(exchange);
                        return;
                    } catch (QueryVariableNotBoundException qvnbe) {
                        response.setInError(HttpStatus.SC_BAD_REQUEST, "error executing aggreation pipeline: " + qvnbe.getMessage());
                        next(exchange);
                        return;
                    }
                    // ***** get data
                    for (BsonDocument obj : agrOutput) {
                        _data.add(obj);
                    }
                    break;
                default:
                    response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown query type");
                    next(exchange);
                    return;
            }
        }

        if (exchange.isComplete()) {
            // if an error occured getting data, the exchange is already closed
            return;
        }

        try {
            var data = new BsonArray();

            _data.stream().forEachOrdered(data::add);

            response.setContent(data);
            response.setCount(data.size());

            response.setContentTypeAsJson();
            response.setStatusCode(HttpStatus.SC_OK);

            // call the ResponseTransformerMetadataHandler if piped in
            next(exchange);
        } catch (IllegalQueryParamenterException ex) {
            response.setInError(
                    HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
            next(exchange);
        }
    }

    /**
     * adds the default variables to the avars document
     *
     * Supports accounts handled by MongoRealAuthenticator,
     * FileRealmAuthenticator and JwtAuthenticationMechanism
     *
     * @param request
     * @param avars
     */
    private void injectAvars(MongoRequest request, BsonDocument avars) {
        // add @page, @pagesize, @limit and @skip to avars to allow handling
        // paging in the aggragation via default page and pagesize qparams
        avars.put("@page", new BsonInt32(request.getPage()));
        avars.put("@pagesize", new BsonInt32(request.getPagesize()));
        avars.put("@limit", new BsonInt32(request.getPagesize()));
        avars.put("@skip", new BsonInt32(request.getPagesize() * (request.getPage() - 1)));

        // add @mongoPermissions to avars
        var mongoPermissions = MongoPermissions.of(request);
        if (mongoPermissions != null) {
            avars.put("@mongoPermissions" ,mongoPermissions.asBson());

            avars.put("@mongoPermissions.projectResponse", mongoPermissions.getProjectResponse() == null
                ? BsonNull.VALUE
                : mongoPermissions.getProjectResponse());

            avars.put("@mongoPermissions.mergeRequest", mongoPermissions.getMergeRequest() == null
                ? BsonNull.VALUE
                : AclVarsInterpolator.interpolateBson(request, mongoPermissions.getMergeRequest()));

            avars.put("@mongoPermissions.readFilter", mongoPermissions.getReadFilter() == null
                ? BsonNull.VALUE
                : AclVarsInterpolator.interpolateBson(request, mongoPermissions.getReadFilter()));

            avars.put("@mongoPermissions.writeFilter", mongoPermissions.getWriteFilter() == null
                ? BsonNull.VALUE
                : AclVarsInterpolator.interpolateBson(request, mongoPermissions.getWriteFilter()));
        } else {
            avars.put("@mongoPermissions", new MongoPermissions().asBson());
            avars.put("@mongoPermissions.projectResponse", BsonNull.VALUE);
            avars.put("@mongoPermissions.mergeRequest", BsonNull.VALUE);
            avars.put("@mongoPermissions.readFilter", BsonNull.VALUE);
            avars.put("@mongoPermissions.writeFilter", BsonNull.VALUE);
        }

        // add @user to avars
        var account = request.getAuthenticatedAccount();

        if (account != null && account instanceof MongoRealmAccount maccount) {
            var ba = maccount.getAccountDocument();

            avars.put("@user", ba);
            ba.keySet().forEach(k -> avars.put("@user.".concat(k), ba.get(k)));
        } else if (account != null && account instanceof FileRealmAccount faccount) {
            var ba = BsonUtils.toBsonDocument(faccount.getAccountProperties());

            avars.put("@user", ba);
            ba.keySet().forEach(k -> avars.put("@user.".concat(k), ba.get(k)));
        } else if (account != null && account instanceof JwtAccount jwtAccount) {
            var bva = BsonUtils.parse(jwtAccount.getJwtPayload());

            if (bva instanceof BsonDocument bda) {
                avars.put("@user", bda);
                bda.keySet().forEach(k -> avars.put("@user.".concat(k), bda.get(k)));
            } else {
                LOGGER.warn("jwt payload is not a json object, returning null account document");
                avars.put("@user", BsonNull.VALUE);
            }
        } else {
            avars.put("@user", BsonNull.VALUE);
        }
    }
}
