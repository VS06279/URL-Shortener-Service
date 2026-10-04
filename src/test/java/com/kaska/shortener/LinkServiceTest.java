package com.kaska.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaska.shortener.api.ApiException;
import com.kaska.shortener.config.ShortenerProperties;
import com.kaska.shortener.domain.ClickContext;
import com.kaska.shortener.domain.Link;
import com.kaska.shortener.service.LinkService;
import com.kaska.shortener.service.ShortCodeGenerator;
import com.kaska.shortener.store.InMemoryLinkRepository;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkServiceTest {

    private LinkService linkService;

    @BeforeEach
    void setUp() {
        ShortenerProperties properties = new ShortenerProperties(
                "http://localhost:8080",
                7,
                new ShortenerProperties.RateLimit(60, 60),
                Map.of("key", "client-a"));
        linkService = new LinkService(
                new InMemoryLinkRepository(), new ShortCodeGenerator(), properties);
    }

    @Test
    void createsLinkWithGeneratedCode() {
        Link link = linkService.create("client-a", "https://akshayapatra.org/donate", null, null);

        assertThat(link.code()).hasSize(7);
        assertThat(link.targetUrl()).isEqualTo("https://akshayapatra.org/donate");
        assertThat(linkService.resolve(link.code())).contains(link);
    }

    @Test
    void honorsCustomAliasAndRejectsDuplicates() {
        linkService.create("client-a", "https://example.org", "donate-2026", null);

        assertThatThrownBy(() -> linkService.create("client-a", "https://other.org", "donate-2026", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void rejectsNonHttpUrls() {
        assertThatThrownBy(() -> linkService.create("client-a", "ftp://example.org/file", null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("http");
    }

    @Test
    void hidesLinksOwnedByAnotherClient() {
        Link link = linkService.create("client-a", "https://example.org", null, null);

        assertThatThrownBy(() -> linkService.requireOwned(link.code(), "client-b"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No link found");
    }

    @Test
    void recordsClicksAsynchronously() {
        Link link = linkService.create("client-a", "https://example.org", null, null);
        ClickContext context = new ClickContext("https://news.example.com/story", "curl/8", "10.0.0.1");

        linkService.recordClick(link.code(), context);
        linkService.recordClick(link.code(), context);

        Awaitility.await().untilAsserted(() -> {
            assertThat(linkService.statsFor(link.code()).totalClicks()).isEqualTo(2);
            assertThat(linkService.statsFor(link.code()).uniqueVisitors()).isEqualTo(1);
            assertThat(linkService.statsFor(link.code()).topReferrers(5))
                    .containsKey("news.example.com");
        });
    }
}
