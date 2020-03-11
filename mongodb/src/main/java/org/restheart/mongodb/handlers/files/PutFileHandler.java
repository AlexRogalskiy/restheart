package org.restheart.mongodb.handlers.files;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.db.GridFsDAO;
import org.restheart.mongodb.db.GridFsRepository;
import org.restheart.handlers.exchange.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.utils.HttpStatus;
import org.restheart.mongodb.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PutFileHandler extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PutFileHandler.class);

    private final GridFsRepository gridFsDAO;
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    /**
     *
     */
    public PutFileHandler() {
        super();
        this.gridFsDAO = new GridFsDAO();
    }

    /**
     *
     * @param next
     */
    public PutFileHandler(PipelinedHandler next) {
        super(next);
        this.gridFsDAO = new GridFsDAO();
    }

    /**
     *
     * @param next
     * @param gridFsDAO
     */
    public PutFileHandler(PipelinedHandler next, GridFsDAO gridFsDAO) {
        super(next);
        this.gridFsDAO = gridFsDAO;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange)
            throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }

        final BsonValue _metadata = request.getContent();

        // must be an object
        if (!_metadata.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            next(exchange);
            return;
        }

        BsonDocument metadata = _metadata.asDocument();

        BsonValue id = request.getDocumentId();

        if (metadata.get("_id") != null
                && metadata.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "_id in content body is different than id in URL");
            next(exchange);
            return;
        }

        metadata.put("_id", id);

        OperationResult result;

        try {
            if (request.getFilePath() != null) {
                result = gridFsDAO
                        .upsertFile(dbsDAO,
                                request.getDBName(),
                                request.getCollectionName(),
                                metadata,
                                request.getFilePath(),
                                id,
                                request.getETag(),
                                request.isETagCheckRequired());
            } else {
                // throw new RuntimeException("error. file data is null");
                // try to pass to next handler in order to PUT new metadata on existing file.
                next(exchange);
                return;
            }
        } catch (MongoWriteException t) {
            if (((MongoException) t).getCode() == 11000) {

                // update not supported
                String errMsg = "file resource update is not yet implemented";
                LOGGER.error(errMsg, t);
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        HttpStatus.SC_NOT_IMPLEMENTED,
                        errMsg);
                next(exchange);
                return;
            }

            throw t;
        }

        response.setDbOperationResult(result);

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
