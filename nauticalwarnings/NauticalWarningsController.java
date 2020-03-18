package fi.liikennevirasto.winvis.nauticalwarnings;

import fi.liikennevirasto.winvis.audit.Audit;
import fi.liikennevirasto.winvis.common.Urls;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Provides access to imported and s124-converted digitraffic nautical warnings
 */
@RestController
@RequestMapping(Urls.UI_API + "nautical-warnings")
public class NauticalWarningsController {

    private NauticalWarningsService nauticalWarningsService;

    @Autowired
    public NauticalWarningsController(NauticalWarningsService nauticalWarningsService) {
        this.nauticalWarningsService = nauticalWarningsService;
    }


    @Audit
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", path = "{status}")
    public List<NauticalWarningEntity> getWarnings(@PathVariable("status") WarningStatus status) {
        switch (status) {
            case EXPIRED:
                return nauticalWarningsService.getExpiredWarnings();
            case ACTIVE:
                return nauticalWarningsService.getActiveWarnings();
            default:
                throw new IllegalArgumentException("Expected either EXPIRED or ACTIVE for status");
        }

    }

}
