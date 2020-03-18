package fi.liikennevirasto.winvis.nauticalwarnings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

@Service
public class NauticalWarningsService {

    private static final Logger logger = LoggerFactory.getLogger(NauticalWarningsService.class);

    private static final DateTimeFormatter ISO_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss'Z'");
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    private final NauticalWarningsRepository warningRepository;
    private final Configuration freemakerConfiguration;
    private final DigitrafficClient digitrafficClient;
    private final SMAClient smaClient;

    @Value("${nautical-warnings.sma.enabled}")
    private boolean smaIntegrationEnabled;


    @Autowired
    public NauticalWarningsService(NauticalWarningsRepository warningRepository, Configuration freemakerConfiguration, DigitrafficClient digitrafficClient, SMAClient smaClient) {
        this.warningRepository = warningRepository;
        this.freemakerConfiguration = freemakerConfiguration;
        this.digitrafficClient = digitrafficClient;
        this.smaClient = smaClient;
    }

    @Transactional
    public void pollForWarnings() {
        logger.info("Polling for Digitraffic nautical warnings warnings");
        List<NauticalWarningEntity> oldWarnings = warningRepository.findAllByExpiredTimeIsNull();
        logger.info("Got {} old warnings from DB.", oldWarnings.size());
        var features = digitrafficClient.fetchWarnings();
        var newWarnings = StreamSupport.stream(features.spliterator(), false)
                .filter(this::filterOutNavigationalWarningsForFishermen)
                .map(this::processGeoJsoNWarningForDatabase)
                .collect(toList());
        logger.info("Polled {} warnings from digitraffic API.", newWarnings.size());

        // Grab ids of all items that were in db so we can filter only new items
        var oldIdsList = oldWarnings.stream()
                .map(NauticalWarningEntity::getId)
                .collect(toList());

        // Grab ids of all items that were in set returned from Digitraffic API
        var idsReturnedFromAPI = newWarnings.stream()
                .map(NauticalWarningEntity::getId)
                .collect(toList());

        var newItemsList = newWarnings.stream()
                .filter(w -> !oldIdsList.contains(w.getId()))
                .collect(toList());
        if (newItemsList.size() > 0) {
            logger.info("Got {} new items that were not yet in database. Adding them.",
                    newItemsList.size());
        }

        // Grab all old warnings, that are not yet expired,
        // but that are also not in new ids list,
        // so we can mark them as expired
        var expiredItemsList = oldWarnings.stream()
                .filter(w -> w.getExpiredTime() == null)
                .filter(w -> !idsReturnedFromAPI.contains(w.getId()))
                .map(this::processExpiredWarning)
                .collect(toList());

        if (expiredItemsList.size() > 0) {
            logger.info("Got {} items that were in the database but do not exist anymore in digitraffic API. Marking them as expired.",
                    expiredItemsList.size());
        }

        // Notify interested parties
        notifyNewItems(newItemsList);
        notifyExpiredItems(expiredItemsList);

        warningRepository.saveAll(newItemsList);
        warningRepository.saveAll(expiredItemsList);

        // Some logging to top it off
        logChangedItems(newItemsList, expiredItemsList);

    }

    private void logChangedItems(List<NauticalWarningEntity> newItemsList, List<NauticalWarningEntity> expiredItemsList) {
        var newIds = String.join(",", newItemsList.stream().map(i -> String.valueOf(i.getId())).collect(toList()));
        var expiredIds = String.join(",", expiredItemsList.stream().map(i -> String.valueOf(i.getId())).collect(toList()));
        if (newItemsList.size() > 0) {
            logger.info("Processed and notified new warnings: {}", newIds);
        }
        if (expiredItemsList.size() > 0) {
            logger.info("Processed and notified expired warnings: {}", expiredIds);
        }
    }


    /**
     * If any new items are found, they are delivered to any interested parties,
     * for example SMA and email recipients.
     *
     * @param newItemsList
     */
    private void notifyNewItems(List<NauticalWarningEntity> newItemsList) {
        if (!smaIntegrationEnabled || newItemsList.size() == 0) {
            logger.info("No new items to notify");
            return;
        }
        logger.info("Notifying interested parties about {} new items.", newItemsList.size());
        if (smaIntegrationEnabled) {
            newItemsList.forEach(smaClient::sendS124Notification);
        }
        logger.info("Notifications were successful.");
    }

    /**
     * If any warnings have disappeared from Digitraffic API they are marked as expired (validity end date is current timestamp),
     * and sent to any interested parties
     *
     * @param expiredItemsList
     */
    private void notifyExpiredItems(List<NauticalWarningEntity> expiredItemsList) {
        if (!smaIntegrationEnabled || expiredItemsList.size() == 0) {
            logger.info("No expired items to notify");
            return;
        }
        logger.info("Notifying interested parties about {} expired items.", expiredItemsList.size());
        expiredItemsList.forEach(smaClient::sendS124Notification);
        logger.info("Notifications were successful.");
    }


    /**
     * When warning is expired (no longer in digitraffic API), its expired time is set to current moment,
     * and S124 document is updated to contain the expiry moment as validity end time
     * To retain original creation times and information related to that, there's some more code
     * to find if from database creation time instead of regenerating it from current moment.
     *
     * @param warning
     * @return
     */
    private NauticalWarningEntity processExpiredWarning(NauticalWarningEntity warning) {
        try {

            var featureNode = new ObjectMapper().readValue(warning.getJsonDocument(), ObjectNode.class);
            var templateParams = findParametersFromMarineWarningJson(featureNode);

            // Overwrite certain time-related fields that should not change by which moment we expire
            var originalValidityStartLocalTime = warning.getCreatedTime();
            ZonedDateTime ldtZonedValidityStartTime = originalValidityStartLocalTime.atZone(ZoneId.systemDefault());
            ZonedDateTime utcZonedValidityStartTime = ldtZonedValidityStartTime.withZoneSameInstant(ZoneOffset.UTC);
            var warningId = featureNode.get("properties").get("id").asLong();
            var lastTwoDigitsOfCurrentYear = (String.format("%d", utcZonedValidityStartTime.getYear())).substring(2);
            String s124Id = buildS124ID(warningId, lastTwoDigitsOfCurrentYear);
            templateParams.put("id", s124Id);
            templateParams.put("year", lastTwoDigitsOfCurrentYear);
            var formattedValidityStartDate = utcZonedValidityStartTime.format(ISO_DATE_FORMATTER);
            templateParams.put("validity_start_date", formattedValidityStartDate);
            var formattedValidityStartTimeUtc = utcZonedValidityStartTime.format(ISO_TIME_FORMATTER);
            templateParams.put("validity_start_time_utc", formattedValidityStartTimeUtc);

            var now = ZonedDateTime.now(ZoneOffset.UTC);
            var expiryTime = now.format(ISO_TIME_FORMATTER);
            var expiryDate = now.format(ISO_DATE_FORMATTER);
            templateParams.put("expiry_time", expiryTime);
            templateParams.put("expiry_date", expiryDate);

            var t = freemakerConfiguration.getTemplate("s124_template.xml");
            var s124Document = FreeMarkerTemplateUtils
                    .processTemplateIntoString(t, templateParams);
            var expiredWarning = new NauticalWarningEntity(
                    warning.getId(),
                    warning.getJsonDocument(),
                    s124Document);
            expiredWarning.setExpiredTime(now.toLocalDateTime());
            return expiredWarning;
        } catch (IOException | TemplateException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildS124ID(long warningId, String lastTwoDigitsOfCurrentYear) {
        return String.format("NW.FI.FTA.L.%d.%s", warningId, lastTwoDigitsOfCurrentYear);
    }

    /**
     * Grab a digitraffic warning feature as input, and process it to a NauticalWarningEntity db object
     * that we are able to store in db.
     *
     * @param feature
     * @return
     */
    private NauticalWarningEntity processGeoJsoNWarningForDatabase(JsonNode feature) {
        try {
            var templateParams = findParametersFromMarineWarningJson(feature);
            var t = freemakerConfiguration.getTemplate("s124_template.xml");
            var s124Document = FreeMarkerTemplateUtils
                    .processTemplateIntoString(t, templateParams);
            return new NauticalWarningEntity(feature.get("properties").get("id").asLong(), feature.toString(), s124Document);
        } catch (IOException | TemplateException e) {
            throw new RuntimeException(e);
        }

    }


    /**
     * We do not include warnings where type is 'Navigatonal warnings for yachtsmen'
     *
     * @param feature
     * @return
     */
    private boolean filterOutNavigationalWarningsForFishermen(JsonNode feature) {
        var warningType = feature.get("properties").get("typeEn").asText();
        var yachtsmenWarning = warningType.equals("Navigational warnings for yachtsmen");
        if (yachtsmenWarning) {
            logger.info("Skipping digitraffic warning with type of Navigational warnings for yachtsmen.");
        }
        return !yachtsmenWarning;
    }


    /**
     * We collect information from original digitraffic warning json,
     * process that information to suitable S124 format, and populate
     * parameters map with that information. This function is the core
     * of the transformation process
     *
     * @param feature
     * @return
     */
    private Map<String, Object> findParametersFromMarineWarningJson(JsonNode feature) {
        var parameters = new HashMap<String, Object>();
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var warningId = feature.get("properties").get("id").asLong();
        var lastTwoDigitsOfCurrentYear = (String.format("%d", now.getYear())).substring(2);
        String s124Id = buildS124ID(warningId, lastTwoDigitsOfCurrentYear);
        parameters.put("id", s124Id);
        parameters.put("warning_number", "" + feature.get("properties").get("number"));
        parameters.put("year", lastTwoDigitsOfCurrentYear);
        parameters.put("general_area", GeoLocationMatcher.findGeneralAreaName(feature));
        var geometryType = feature.get("geometry").get("type").asText();
        parameters.put("geometry_type", geometryType);
        var coordinates = (ArrayNode) feature.get("geometry").get("coordinates");
        var transformedCoordinates = transformCoordinateFormat(geometryType, coordinates);
        parameters.put("gml_pos_coordinates", transformedCoordinates);
        // Fix: Some entries do not have creationTime set, at least in test environment, so we use current timestamp to make the document valid
        if (feature.get("properties").hasNonNull("creationTime")) {
            var creationTime = feature.get("properties").get("creationTime").asText();
            String formattedCreationTime = getDatePartFromIsoDatetime(creationTime);
            parameters.put("creation_time", formattedCreationTime);
        } else {
            parameters.put("creation_time", ISO_DATE_FORMATTER.format(now));
        }
        var publishingTime = feature.get("properties").get("publishingTime").asText();
        String formattedPublishingTime = getDatePartFromIsoDatetime(publishingTime);
        parameters.put("publishing_time", formattedPublishingTime);
        var formattedValidityStartDate = ISO_DATE_FORMATTER.format(now);
        parameters.put("validity_start_date", formattedValidityStartDate);
        var formattedValidityStartTimeUtc = ISO_TIME_FORMATTER.format(now);
        parameters.put("validity_start_time_utc", formattedValidityStartTimeUtc);
        var locationEn = feature.get("properties").get("locationEn").asText();
        parameters.put("title_text", StringEscapeUtils.escapeXml10(locationEn));
        var contentsEn = feature.get("properties").get("contentsEn").asText();
        parameters.put("warning_subject_text", StringEscapeUtils.escapeXml10(contentsEn));
        var typeOfWarning = calculateWarningType(feature);
        parameters.put("type_of_warning", typeOfWarning);
        // These are hardcoded for now, should form a box around Nordic waters
        parameters.put("lower_corner", "-6.0000 40.0000");
        parameters.put("upper_corner", "45.0000 65.0000");
        return parameters;
    }


    /**
     * Point/area coordinates are originally in json array in digitraffic warning.
     * S124 uses different format where values and value  pairs are simply separated by space,
     * and value pairs are in format: 'lat,lon'. This function transforms both Point
     * and Polygon types to such representation, effectively dismantling the
     * original array structures from around the values.
     *
     * @param geometryType
     * @param coordinates
     * @return
     */
    public String transformCoordinateFormat(String geometryType, ArrayNode coordinates) {
        switch (geometryType) {
            case "Point":
                var lat = coordinates.get(1).asText();
                var lon = coordinates.get(0).asText();
                return String.format("%s %s", lat, lon);
            case "Polygon":
                // Polygon has array of shapes which contains arrays of points which all contain array of coordinates
                StringBuilder polygonCoordinatesStringBuilder = new StringBuilder();
                coordinates.get(0).iterator().forEachRemaining(coord ->
                        polygonCoordinatesStringBuilder.append(
                                coord.get(1).asText())
                                .append(' ')
                                .append(coord.get(0).asText())
                                .append(' '));
                return polygonCoordinatesStringBuilder.toString().trim();
            case "LineString":
                StringBuilder linestringCoordinatesStringBuilder = new StringBuilder();
                coordinates.iterator().forEachRemaining(coord ->
                        linestringCoordinatesStringBuilder.append(
                                coord.get(1).asText())
                                .append(' ')
                                .append(coord.get(0).asText())
                                .append(' '));
                return linestringCoordinatesStringBuilder.toString().trim();
            default:
                throw new RuntimeException("Unsupported geometry type encountered: " + geometryType);
        }
    }

    private static String getDatePartFromIsoDatetime(String publishingTime) {
        return publishingTime.split("T")[0];
    }


    private String calculateWarningType(JsonNode feature) {
        var typeEn = feature.get("properties").get("typeEn").asText().trim();
        switch (typeEn) {
            case "NAVIGATIONAL WARNING":
                return "local";
            case "COASTAL":
            case "NAVTEX COASTAL":
                return "coastal";
            default:
                return "[UNKNOWN]";
        }
    }


    /**
     * Clean up db of warnings that were expired more than a month ago, so they don't clutter up database
     */
    @Transactional
    public void deleteOldExpired() {
        LocalDateTime expiryDate = LocalDateTime.now().minus(Period.ofMonths(1));
        logger.info("Deleting items expired before " + expiryDate);
        warningRepository.deleteByExpiredTimeBefore(expiryDate);
    }

    /**
     * Get all nautical warnings that are in active state eg not expired
     *
     * @return
     */
    public List<NauticalWarningEntity> getActiveWarnings() {
        return warningRepository.findAllByExpiredTimeIsNull();

    }

    /**
     * Get all nautical warnings that are in expired state eg not available in
     * digitraffic API anymore, thus marked as expired and S124 document has
     * expirytime set.
     *
     * @return
     */
    public List<NauticalWarningEntity> getExpiredWarnings() {
        return warningRepository.findAllByExpiredTimeIsNotNull();
    }


    /**
     * Sometimes SMA warning deliveries may fail due to network errors or SMA service
     * not being up. This function will find all those items from database, and will try
     * to re-send them. If successful, these items will be marked as delivered. If not successful,
     * delivery attempt will be made again.
     */
    public void retryFailedSmaNotifications() {
        List<NauticalWarningEntity> failedWarnings = warningRepository.findAllBySmaDeliveryTimeIsNull();
        if (failedWarnings.size() == 0) {
            return;
        }
        failedWarnings.forEach(System.out::println);
        logger.info("Retrying {} failed SMA nautical warning notifications.", failedWarnings.size());
        failedWarnings.forEach(smaClient::sendS124Notification);
        warningRepository.saveAll(failedWarnings);
        logger.info("All failed warnings were retried.");
    }
}
