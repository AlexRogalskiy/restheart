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
package org.restheart.mongodb.handlers.database;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.OperationResult;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.handlers.injectors.LocalCachesSingleton;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteDBHandler extends PipelinedHandler {
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    /**
     * Creates a new instance of DeleteDBHandler
     */
    public DeleteDBHandler() {
        super();
    }

    /**
     * Creates a new instance of DeleteDBHandler
     *
     * @param next
     */
    public DeleteDBHandler(PipelinedHandler next) {
        super(next);
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

        String etag = request.getETag();

        OperationResult result = dbsDAO.deleteDatabase(
                request.getClientSession(),
                request.getDBName(),
                etag,
                request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_CONFLICT,
                    "The database's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header.");
            next(exchange);
            return;
        }

        if (result.getEtag() != null) {
            exchange.getResponseHeaders().put(Headers.ETAG, result.getEtag().toString());
        }

        response.setStatusCode(result.getHttpCode());

        LocalCachesSingleton.getInstance().invalidateDb(request.getDBName());

        next(exchange);
    }
}
