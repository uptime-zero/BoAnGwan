package com.boangwan.collector;

import com.boangwan.domain.GuidType;
import com.rometools.rome.feed.synd.SyndEntry;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class GuidExtractor {

    public String extract(SyndEntry entry, GuidType guidType) {
        return switch (guidType) {
            case LINK_IDX -> extractIdxFromUrl(entry.getLink());
            case GUID_TAG -> entry.getUri() != null ? entry.getUri() : entry.getLink();
        };
    }

    private String extractIdxFromUrl(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            String query = uri.getQuery();
            if (query == null) return url;
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "idx".equals(kv[0])) {
                    return kv[1];
                }
            }
        } catch (IllegalArgumentException ignored) {}
        return url;
    }
}
