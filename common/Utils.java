package fi.liikennevirasto.winvis.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Common static utilities
 */
public class Utils {

    private Utils() { throw new IllegalStateException("Utility class"); }

    /**
     * Returns Pageable with descending default sorting by created timestamp.
     */
    public static Pageable getPageable(int page, int size) {
        return PageRequest.of(
                page < 0 ? 0 : page,
                size,
                new Sort(Sort.Direction.DESC, "created"));
    }

}
