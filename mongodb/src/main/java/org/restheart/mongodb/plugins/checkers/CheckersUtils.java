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
package org.restheart.mongodb.plugins.checkers;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CheckersUtils {
    /**
     *
     * @param context
     * @return
     */
    @SuppressWarnings( "deprecation" )
    public static boolean isBulkRequest(RequestContext context) {
        return context.isBulkDocuments() || context.getContent().isArray();
    }
    
    /**
     *
     * @param request
     * @return
     */
    public static boolean isBulkRequest(BsonRequest request) {
        return request.isBulkDocuments() || request.getContent().isArray();
    }

    /**
     *
     * @param content
     * @return
     */
    public static boolean doesRequestUsesUpdateOperators(BsonValue content) {
        if (content.isDocument()) {
            return JsonUtils.containsUpdateOperators(content.asDocument());
        } else if (content.isArray()) {
            BsonArray objs = content.asArray();

            return objs.stream().allMatch(obj -> {
                if (obj.isDocument()) {
                    return doesRequestUsesUpdateOperators(obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }

    /**
     *
     * @param content
     * @return
     */
    public static boolean doesRequestUsesDotNotation(BsonValue content) {
        if (content.isDocument()) {
            BsonDocument obj = content.asDocument();

            return obj.keySet().stream().anyMatch(key -> {
                return key.contains(".");
            });
        } else if (content.isArray()) {
            BsonArray objs = content.asArray();

            return objs.stream().anyMatch(obj -> {
                if (obj.isDocument()) {
                    return doesRequestUsesDotNotation(obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }

    private CheckersUtils() {
    }
}
