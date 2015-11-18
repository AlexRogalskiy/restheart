/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.handlers.database;

import com.mongodb.DBObject;
import org.restheart.Configuration;
import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.List;
import org.bson.types.ObjectId;
import org.restheart.hal.AbstractRepresentationFactory;
import org.restheart.handlers.RequestContext.HAL_MODE;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DBRepresentationFactory extends AbstractRepresentationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBRepresentationFactory.class);

    public DBRepresentationFactory() {
    }

    @Override
    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep = createRepresentation(exchange, context, requestPath);

        addProperties(rep, context);
        
        addSizeAndTotalPagesProperties(size, context, rep);

        addEmbeddedData(embeddedData, rep, requestPath);

        if (context.getHalMode() == HAL_MODE.FULL
                || context.getHalMode() == HAL_MODE.F) {

            addPaginationLinks(exchange, context, size, rep);

            addLinkTemplates(exchange, context, rep, requestPath);

            // curies
            rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL
                    + "/{rel}.html", true), true);
        } else {
            // empty curies section. this is needed due to HAL browser issue
            // https://github.com/mikekelly/hal-browser/issues/71
            rep.addLinkArray("curies");
        }

        return rep;
    }

    private void addProperties(final Representation rep, RequestContext context) {
        final DBObject dbProps = context.getDbProps();

        rep.addProperties(dbProps);

        if (dbProps != null) {
            Object etag = dbProps.get("_etag");

            if (etag != null && etag instanceof ObjectId) {
                rep.addProperty("_lastupdated_on", Instant.ofEpochSecond(((ObjectId) etag).getTimestamp()).toString());
            }
        }
    }

    private void addEmbeddedData(
            final List<DBObject> embeddedData,
            final Representation rep,
            final String requestPath) {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);

            if (!embeddedData.isEmpty()) {
                embeddedCollections(embeddedData, requestPath, rep);
            }
        } else {
            rep.addProperty("_returned", 0);
        }
    }

    private void addLinkTemplates(
            final HttpServerExchange exchange,
            final RequestContext context,
            final Representation rep,
            final String requestPath) {
        String parentPath = URLUtils.getParentPath(requestPath);

        // link templates and curies
        if (context.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link("rh:root", parentPath));
        }

        if (parentPath.endsWith("/")) {
            rep.addLink(new Link("rh:db", URLUtils.removeTrailingSlashes(URLUtils.getParentPath(requestPath)) + "{dbname}", true));
        } else {
            rep.addLink(new Link("rh:db", URLUtils.removeTrailingSlashes(URLUtils.getParentPath(requestPath)) + "/{dbname}", true));
        }

        rep.addLink(new Link("rh:coll", requestPath + "/{collname}", true));
        rep.addLink(new Link("rh:bucket", requestPath + "/{bucketname}" + RequestContext.FS_FILES_SUFFIX, true));

        rep.addLink(new Link("rh:paging", requestPath + "{?page}{&pagesize}", true));

        rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL
                + "/{rel}.html", true), true);
    }

    private void embeddedCollections(
            final List<DBObject> embeddedData,
            final String requestPath,
            final Representation rep) {
        embeddedData.stream().forEach((d) -> {
            Object _id = d.get("_id");

            if (_id != null && _id instanceof String) {
                String id = (String) _id;
                Representation nrep = new Representation(requestPath + "/" + id);

                nrep.addProperties(d);

                if (id.endsWith(RequestContext.FS_FILES_SUFFIX)) {
                    nrep.addProperty("_type", RequestContext.TYPE.FILES_BUCKET.name());
                    rep.addRepresentation("rh:bucket", nrep);
                } else {
                    nrep.addProperty("_type", RequestContext.TYPE.COLLECTION.name());
                    rep.addRepresentation("rh:coll", nrep);
                }
            } else {
                // this shoudn't be possible
                LOGGER.error("Collection missing string _id field: {}", _id);
            }
        });
    }
}
