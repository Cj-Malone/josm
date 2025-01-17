// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.tools.Logging;

/**
 * Convert {@link TestError} to MapRoulette Tasks
 * @author Taylor Smock
 * @since 18365
 */
public class GeoJSONMapRouletteWriter extends GeoJSONWriter {

    /**
     * Constructs a new {@code GeoJSONWriter}.
     * @param ds The originating OSM dataset
     */
    public GeoJSONMapRouletteWriter(DataSet ds) {
        super(ds);
        super.setOptions(Options.RIGHT_HAND_RULE, Options.WRITE_OSM_INFORMATION);
    }

    /**
     * Convert a test error to a string
     * @param testError The test error to convert
     * @return The MapRoulette challenge object
     */
    public Optional<JsonObject> write(final TestError testError) {
        final JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        final JsonArrayBuilder featuresBuilder = Json.createArrayBuilder();
        final JsonObjectBuilder propertiesBuilder = Json.createObjectBuilder();
        propertiesBuilder.add("message", testError.getMessage());
        Optional.ofNullable(testError.getDescription()).ifPresent(description -> propertiesBuilder.add("description", description));
        propertiesBuilder.add("code", testError.getCode());
        propertiesBuilder.add("fixable", testError.isFixable());
        propertiesBuilder.add("severity", testError.getSeverity().toString());
        propertiesBuilder.add("severityInteger", testError.getSeverity().getLevel());
        propertiesBuilder.add("test", testError.getTester().getName());
        Stream.concat(testError.getPrimitives().stream(), testError.getHighlighted().stream()).distinct().map(p -> {
            if (p instanceof OsmPrimitive) {
                return p;
            } else if (p instanceof WaySegment) {
                return ((WaySegment) p).toWay();
            }
            Logging.trace("Could not convert {0} to an OsmPrimitive", p);
            return null;
        }).filter(Objects::nonNull).distinct().map(OsmPrimitive.class::cast)
                .forEach(primitive -> super.appendPrimitive(primitive, featuresBuilder));
        final JsonArray featureArray = featuresBuilder.build();
        final JsonArrayBuilder featuresMessageBuilder = Json.createArrayBuilder();
        if (featureArray.isEmpty()) {
            Logging.trace("Could not generate task for {0}", testError.getMessage());
            return Optional.empty();
        }
        JsonObject primitive = featureArray.getJsonObject(0);
        JsonObjectBuilder replacementPrimitive = Json.createObjectBuilder(primitive);
        final JsonObjectBuilder properties;
        if (primitive.containsKey("properties") && primitive.get("properties").getValueType() == JsonValue.ValueType.OBJECT) {
            properties = Json.createObjectBuilder(primitive.getJsonObject("properties"));
        } else {
            properties = Json.createObjectBuilder();
        }
        properties.addAll(propertiesBuilder);
        replacementPrimitive.add("properties", properties);
        featuresMessageBuilder.add(replacementPrimitive);
        for (int i = 1; i < featureArray.size(); i++) {
            featuresMessageBuilder.add(featureArray.get(i));
        }
        // For now, don't add any cooperativeWork objects, as JOSM should be able to find the fixes.
        // This should change if the ValidatorCLI can use plugins (especially those introducing external data, like
        // the ElevationProfile plugin (which provides elevation data)).
        jsonObjectBuilder.add("type", "FeatureCollection");
        jsonObjectBuilder.add("features", featuresMessageBuilder);
        return Optional.of(jsonObjectBuilder.build());
    }
}
