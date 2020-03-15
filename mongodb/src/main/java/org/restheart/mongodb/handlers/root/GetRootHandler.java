/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.mongodb.handlers.root;

import com.google.common.annotations.VisibleForTesting;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.db.Database;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.handlers.injectors.LocalCachesSingleton;
import org.restheart.mongodb.representation.Resource;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetRootHandler extends PipelinedHandler {
    private Database dbsDAO = new DatabaseImpl();

    /**
     *
     */
    public GetRootHandler() {
        super();
    }

    /**
     *
     * @param next
     */
    public GetRootHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param next
     * @param dbsDAO
     */
    @VisibleForTesting
    public GetRootHandler(PipelinedHandler next, Database dbsDAO) {
        super(next);
        this.dbsDAO = dbsDAO;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        int size = 0;

        List<BsonDocument> data = new ArrayList<>();

        if (request.getPagesize() >= 0) {
            List<String> _dbs = dbsDAO.getDatabaseNames(
                    request.getClientSession());

            // filter out reserved resources
            List<String> dbs = _dbs.stream()
                    .filter(db -> !RequestContext.isReservedResourceDb(db))
                    .collect(Collectors.toList());

            if (dbs == null) {
                dbs = new ArrayList<>();
            }

            size = dbs.size();

            if (request.getPagesize() > 0) {
                Collections.sort(dbs); // sort by id

                // apply page and pagesize
                dbs = dbs.subList((request.getPage() - 1) * request.getPagesize(),
                        (request.getPage() - 1) * request.getPagesize()
                        + request.getPagesize() > dbs.size()
                        ? dbs.size()
                        : (request.getPage() - 1) * request.getPagesize()
                        + request.getPagesize());

                dbs.stream().map((db) -> {
                    if (LocalCachesSingleton.isEnabled()) {
                        return LocalCachesSingleton.getInstance()
                                .getDBProperties(db);
                    } else {
                        return dbsDAO.getDatabaseProperties(
                                request.getClientSession(),
                                db);
                    }
                }
                ).forEach((item) -> {
                    data.add(item);
                });
            }
        }

        response.setContent(new RootRepresentationFactory().
                getRepresentation(exchange, data, size)
                .asBsonDocument());

        response.setContentType(Resource.HAL_JSON_MEDIA_TYPE);
        response.setStatusCode(HttpStatus.SC_OK);

        next(exchange);
    }
}
