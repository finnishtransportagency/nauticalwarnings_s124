package fi.liikennevirasto.winvis.nauticalwarnings;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * This converter simply helps convert REST API parameters to set of predetermined enum values, easier
 * to use in code
 */
@Component
public class WarningStatusConverter implements Converter<String, WarningStatus> {


    @Override
    public WarningStatus convert(String s) {
        return WarningStatus.fromValue(s);
    }
}
