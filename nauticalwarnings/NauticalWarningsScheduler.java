package fi.liikennevirasto.winvis.nauticalwarnings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@ConditionalOnProperty(name = "nautical-warnings.poller.enabled", havingValue = "true")
public class NauticalWarningsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NauticalWarningsScheduler.class);
    private static final int DEFAULT_INITIAL_DELAY = 10000;


    private NauticalWarningsService service;

    @Autowired
    public NauticalWarningsScheduler(NauticalWarningsService service) {
        this.service = service;
    }

    @Value("${nautical-warnings.poller.frequency}")
    private int pollingFrequency;

    @PostConstruct
    public void init() {
        logger.info(String.format("Marine Warnings system initialized, polling every %d minutes", pollingFrequency / 60000));
    }

    @Scheduled(initialDelay = DEFAULT_INITIAL_DELAY, fixedDelayString = "${nautical-warnings.poller.frequency}")
    public void fetchMarineWarnings() {
        service.deleteOldExpired();
        service.pollForWarnings();
        service.retryFailedSmaNotifications();
    }

}
