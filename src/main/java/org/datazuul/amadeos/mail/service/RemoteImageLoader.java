package org.datazuul.amadeos.mail.service;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Downloads the remote images of an HTML message after the user allowed it. Runs in the background
 * before rendering, so the renderer itself never opens network connections and never blocks the EDT.
 */
public class RemoteImageLoader {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteImageLoader.class);
    static final int MAX_IMAGES = 60;
    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Image URLs referenced by {@code img src} in the (unsanitised-for-remote) document. */
    public static Set<String> imageUrls(Document document) {
        Set<String> urls = new LinkedHashSet<>();
        for (Element img : document.select("img[src]")) {
            String src = img.attr("src").strip();
            String lower = src.toLowerCase(Locale.ROOT);
            if (lower.startsWith("https:") || lower.startsWith("http:")) urls.add(src);
            if (urls.size() >= MAX_IMAGES) break;
        }
        return urls;
    }

    /** Downloads the images in parallel; failed downloads are left out. */
    public Map<String, byte[]> load(Set<String> urls) {
        Map<String, byte[]> result = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<?>> futures = new LinkedHashMap<>();
            for (String url : urls) futures.put(url, executor.submit(() -> fetch(url, result)));
            for (Future<?> future : futures.values()) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        }
        return result;
    }

    private void fetch(String url, Map<String, byte[]> result) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", MessageComposer.MAILER)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() / 100 != 2) return;
                byte[] data = body.readNBytes(MAX_BYTES + 1);
                if (data.length <= MAX_BYTES) result.put(url, data);
            }
        } catch (IOException | IllegalArgumentException e) {
            LOG.debug("Remote image {} not loaded: {}", url, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
