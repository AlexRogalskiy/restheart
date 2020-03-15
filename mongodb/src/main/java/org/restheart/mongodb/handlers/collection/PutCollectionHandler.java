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
package org.restheart.mongodb.handlers.collection;

import com.google.common.annotations.VisibleForTesting;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.OperationResult;
import org.restheart.mongodb.db.Database;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.handlers.injectors.LocalCachesSingleton;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutCollectionHandler extends PipelinedHandler {
    private Database dbsDAO = new DatabaseImpl();
    /**
     * Creates a new instance of PutCollectionHandler
     */
    public PutCollectionHandler() {
        super();
    }

    /**
     * Creates a new instance of PutCollectionHandler
     *
     * @param next
     */
    public PutCollectionHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of PutCollectionHandler
     *
     * @param next
     * @param dbsDAO
     */
    @VisibleForTesting
    public PutCollectionHandler(PipelinedHandler next, Database dbsDAO) {
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

        BsonValue _content = request.getContent();

        if (_content == null) {
            _content = new BsonDocument();
        }

        // cannot PUT an array
        if (!_content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            next(exchange);
            return;
        }

        final BsonDocument content = _content.asDocument();

        if (RequestHelper.isInvalidMetadata(content, exchange)) {
            next(exchange);
            return;
        }

        boolean updating = request.getCollectionProps() != null;

        OperationResult result = dbsDAO.upsertCollection(
                request.getClientSession(),
                request.getDBName(),
                request.getCollectionName(),
                content,
                request.getETag(),
                updating, false,
                request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // invalidate the cache collection item
        LocalCachesSingleton.getInstance().invalidateCollection(
                request.getDBName(),
                request.getCollectionName());

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_CONFLICT,
                    "The collection's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header.");
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
