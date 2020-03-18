package fi.liikennevirasto.winvis.nauticalwarnings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import freemarker.template.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class DigitrafficClient {

    private static final Logger logger = LoggerFactory.getLogger(DigitrafficClient.class);

    @Value("${nautical-warnings.digitraffic.url}")
    private String digitrafficUrl;

    private RestTemplate restTemplate;

    public DigitrafficClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .build();
    }

    @PostConstruct
    public void init() {
        logger.info("Init: Preparing to poll nautical warnings from {}", this.digitrafficUrl);
    }


    /**
     * Digitraffic API serves GeoJson format FeatureCollection, so we grab
     * it as ArrayNode of GeoJson Feature objects
     *
     * @return
     */
    public ArrayNode fetchWarnings() {


//        try {
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(digitrafficUrl))
//                    .header("Accept", "application/json")
//                    .GET()
//                    .build();
            var response = restTemplate.getForObject(digitrafficUrl, JsonNode.class);
//            var httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//            String body = httpResponse.body();
//            int statusCode = httpResponse.statusCode();
//            if (statusCode != 200) {
//                throw new IntegrationException(body);
//            }
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode result = mapper.readTree(body);
            return (ArrayNode)response.get("features");
//        } catch (IOException | InterruptedException e) {
//            throw new IntegrationException(e);
//        }


    }
}
