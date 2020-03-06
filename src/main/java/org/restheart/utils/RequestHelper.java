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
package org.restheart.utils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.db.OperationResult;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.metadata.CheckerMetadata;
import org.restheart.metadata.Relationship;
import org.restheart.metadata.TransformerMetadata;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestHelper {

    private RequestHelper() {
    }

    /**
     *
     * @param exchange
     * @param etag
     * @return
     */
    public static boolean checkReadEtag(HttpServerExchange exchange, BsonObjectId etag) {
        if (etag == null) {
            return false;
        }

        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_NONE_MATCH);

        return vs == null || vs.getFirst() == null
                ? false
                : vs.getFirst().equals(etag.getValue().toString());
    }

    /**
     *
     * @param exchange
     * @return the etag ObjectId value or null in case the IF_MATCH header is
     * not present. If the header contains an invalid ObjectId string value
     * returns a new ObjectId (the check will fail for sure)
     */
    public static ObjectId getWriteEtag(HttpServerExchange exchange) {
        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_MATCH);

        return vs == null || vs.getFirst() == null ? null : getEtagAsObjectId(vs.getFirst());
    }

    /**
     *
     * @param etag
     * @return
     */
    private static ObjectId getEtagAsObjectId(String etag) {
        if (etag == null) {
            return null;
        }

        if (ObjectId.isValid(etag)) {
            return new ObjectId(etag);
        } else {
            return new ObjectId();
        }
    }

    /**
     *
     * @param content
     * @param exchange
     * @return true if content contains invalid metata. In this case it also
     * invoke ResponseHelper.endExchangeWithMessage() on the exchange and the
     * caller must invoke next() and return
     * @throws Exception
     */
    public static boolean isInvalidMetadata(BsonDocument content, HttpServerExchange exchange) throws Exception {
        // check RELS metadata
        if (content.containsKey(Relationship.RELATIONSHIPS_ELEMENT_NAME)) {
            try {
                Relationship.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong relationships definition. "
                        + ex.getMessage(), ex);
                return true;
            }
        }
        // check RT metadata
        if (content.containsKey(TransformerMetadata.RTS_ELEMENT_NAME)) {
            try {
                TransformerMetadata.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong representation transformer definition. "
                        + ex.getMessage(), ex);
                return true;
            }
        }
        // check SC metadata
        if (content.containsKey(CheckerMetadata.ROOT_KEY)) {
            try {
                CheckerMetadata.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong checker definition. "
                        + ex.getMessage(), ex);
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param content
     * @param exchange
     * @return true if content is not acceptable. In this case it also
     * invoke ResponseHelper.endExchangeWithMessage() on the exchange and the
     * caller must invoke next() and return
     * @throws Exception
     */
    public static boolean isNotAcceptableContent(BsonValue content, HttpServerExchange exchange) throws Exception {
        // cannot proceed with no data
        if (content == null) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            return true;
        }
        // cannot proceed with an array
        if (!content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            return true;
        }
        if (content.asDocument().isEmpty()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            return true;
        }
        return false;
    }

    /**
     *
     * warn side effect: invokes BsonResponse.wrap(exchange).setDbOperationResult(result)
     * @param result
     * @param exchange
     * @return true if response is in coflict. In this case it also
     * invoke ResponseHelper.endExchangeWithMessage() on the exchange and the
     * caller must invoke next() and return
     * @throws Exception
     */
    public static boolean isResponseInConflict(OperationResult result, HttpServerExchange exchange) throws Exception {
        BsonResponse.wrap(exchange).setDbOperationResult(result);
        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }
        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_CONFLICT,
                    "The ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header");
            return true;
        }
        // handle the case of duplicate key error
        if (result.getHttpCode() == HttpStatus.SC_EXPECTATION_FAILED) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_EXPECTATION_FAILED,
                    ResponseHelper.getMessageFromErrorCode(11000));
            return true;
        }
        return false;
    }

}
