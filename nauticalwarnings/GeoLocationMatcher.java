package fi.liikennevirasto.winvis.nauticalwarnings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.wololo.geojson.Feature;
import org.wololo.geojson.GeoJSONFactory;
import org.wololo.jts2geojson.GeoJSONReader;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

@Service
public class GeoLocationMatcher {


    private static String shapeFile;

    static {
        try (var inputStream =
                     GeoLocationMatcher.class
                             .getResourceAsStream(
                                     "/nautical-warnings/nw-sea-areas-converted.json")) {
            shapeFile = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Since S124 uses a restricted set of area names, we cannot just use any name, but instead we need to
     * figure correct area name out based on geojson geometry coordinates
     *
     * @param feature
     * @return
     */
    public static String findGeneralAreaName(JsonNode feature) {

        try {
            var featureCollectionRootNode = new ObjectMapper().readValue(GeoLocationMatcher.shapeFile, ObjectNode.class);
            var shapeFeatureNodes = featureCollectionRootNode.withArray("features");
            var matchingFeatures = StreamSupport.stream(shapeFeatureNodes.spliterator(), false)
                    .filter(shapeFeatureNode -> isPointInShape(feature, shapeFeatureNode))
                    .collect(toList());
            if (matchingFeatures.size() == 1) {
                return matchingFeatures.get(0).get("properties").get("ENG_UID").asText();
            } else {
                return "Baltic sea";
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    /**
     * Simple algorithm to compare two JSON nodes, and find out if original feature
     * geometry is located inside shape feature geometry
     *
     * @param originalFeature
     * @param shapeFeatureNode
     * @return
     */
    private static boolean isPointInShape(JsonNode originalFeature, JsonNode shapeFeatureNode) {
        Feature feature1 = (Feature) GeoJSONFactory.create(originalFeature.toString());
        Feature feature2 = (Feature) GeoJSONFactory.create(shapeFeatureNode.toString());
        var geoJSONReader = new GeoJSONReader();
        var originalGeometry = geoJSONReader.read(feature1.getGeometry());
        var shapeFileGeometry = geoJSONReader.read(feature2.getGeometry());
        if (shapeFileGeometry.contains(originalGeometry)) {
            return true;
        } else {
            return false;
        }
    }


}
