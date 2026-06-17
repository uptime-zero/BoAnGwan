package com.boangwan.collector;

import com.boangwan.domain.GuidType;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuidExtractorTest {

    private final GuidExtractor extractor = new GuidExtractor();

    @Test
    void LINK_IDX_타입은_idx_파라미터값_추출() {
        SyndEntry entry = new SyndEntryImpl();
        entry.setLink("https://www.boannews.com/media/view.asp?idx=144161&kind=1");

        String guid = extractor.extract(entry, GuidType.LINK_IDX);

        assertThat(guid).isEqualTo("144161");
    }

    @Test
    void LINK_IDX_타입에서_idx_없으면_URL_전체_반환() {
        SyndEntry entry = new SyndEntryImpl();
        entry.setLink("https://example.com/article/123");

        String guid = extractor.extract(entry, GuidType.LINK_IDX);

        assertThat(guid).isEqualTo("https://example.com/article/123");
    }

    @Test
    void GUID_TAG_타입은_entry_uri_반환() {
        SyndEntry entry = new SyndEntryImpl();
        entry.setUri("unique-guid-123");
        entry.setLink("https://example.com/article");

        String guid = extractor.extract(entry, GuidType.GUID_TAG);

        assertThat(guid).isEqualTo("unique-guid-123");
    }

    @Test
    void GUID_TAG_타입에서_uri_없으면_link_반환() {
        SyndEntry entry = new SyndEntryImpl();
        entry.setLink("https://example.com/article");

        String guid = extractor.extract(entry, GuidType.GUID_TAG);

        assertThat(guid).isEqualTo("https://example.com/article");
    }
}
