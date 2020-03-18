package fi.liikennevirasto.winvis.nauticalwarnings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;

/**
 * This client contacts to SMA API to send an S124 XML message to it using REST POST API.
 * We handle basic authentication here, and any errors will cause IntegrationException, or
 * other runtime exceptions to be thrown
 */
@Service
public class SMAClient {

    private static final Logger logger = LoggerFactory.getLogger(SMAClient.class);

    @Value("${nautical-warnings.sma.enabled}")
    private boolean smaIntegrationEnabled;
    @Value("${nautical-warnings.sma.url}")
    private String smaS124Url;
    @Value("${nautical-warnings.sma.user}")
    private String smaS124Username;
    @Value("${nautical-warnings.sma.password}")
    private String smaS124Password;
    @Value("${request.timeoutSeconds.default:60}")
    private int timeoutSecondsDefault;

    private RestTemplate restTemplate;
    private HttpHeaders httpHeaders;


    @Autowired
    public SMAClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeoutSecondsDefault * 1000)
                .setReadTimeout(timeoutSecondsDefault * 1000)
                .build();
    }


    @PostConstruct
    public void init() {
        if (this.smaIntegrationEnabled) {
            logger.info("SMA Integration is toggled on for Nautical Warnings. Notifications are sent to {}", smaS124Url);
        }
        this.httpHeaders = new HttpHeaders() {{
            String auth = smaS124Username + ":" + smaS124Password;
            byte[] encodedAuth = Base64.getEncoder().encode(
                    auth.getBytes(Charset.forName("UTF-8")));
            String authHeader = "Basic " + new String(encodedAuth);
            set("Authorization", authHeader);
            set("Content-Type", "text/xml; charset=utf-8");
        }};
    }

    /**
     * Accept item and url as parameter, post given item to given url.
     * Note: This is for XML S124 payloads
     * Note: We also include basic authentication
     * <p>
     * Note: In case of error, we just log and continue, so all warnings get handled
     *
     * @param warning NauticalWarningEntity containing converted S124 document
     */

    public void sendS124Notification(NauticalWarningEntity warning) {
        warning.setSmaDeliveryTime(null);
        if (warning.getExpiredTime() == null) {
            logger.info("Sending new S124 message with warning id {} for SMA API", warning.getId());
        } else {
            logger.info("Sending expired S124 message with warning id {} for SMA API", warning.getId());
        }

        var request = new RequestEntity<>(
                warning.getS124Document(),
                httpHeaders,
                HttpMethod.POST,
                URI.create(smaS124Url),
                Void.class);

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);

        long beginTime = System.nanoTime();
        long endTime;
        try {
            restTemplate.exchange(smaS124Url, HttpMethod.POST, request, Void.class);
            warning.setSmaDeliveryTime(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("Exception connecting SMA. StatusCode: {}, Statustext: {}, Errormessage: {}",
                    e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString());
            logger.error("Offending warning document:\n{} ", warning.getS124Document());
        } catch (RestClientException e) {
            logger.error("Unspecified exception connecting to SMA." +
                            " Errormessage: {}",
                    e.getMessage());
            logger.error("Offending warning document:\n{} ", warning.getS124Document());
        }
        endTime = System.nanoTime();
        logger.info("Call took {}ms", df.format((endTime - beginTime) / 1000000.0));
    }


}
