package dev.irltunes.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.irltunes.IrlTunes;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/** Loads and downsizes cover art. Runs on the media thread, never on the render thread. */
final class CoverArt {
    static final int SIZE = 128;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Small LRU so flipping between two tracks does not refetch. Null values mean "no art found". */
    private final Map<String, CoverImage> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CoverImage> e) {
            return size() > 24;
        }
    };

    @Nullable CoverImage resolve(TrackInfo t, boolean onlineLookup) {
        String key = t.key();
        if (cache.containsKey(key)) return cache.get(key);
        CoverImage img = null;
        try {
            if (t.coverBytes() != null) {
                img = decode(t.coverBytes());
            } else if (t.coverUrl() != null) {
                img = decode(download(t.coverUrl()));
            }
            if (img == null && onlineLookup && !t.title().isBlank()) {
                String url = lookupItunes(t.artist(), t.title());
                if (url != null) img = decode(download(url));
            }
        } catch (IOException | RuntimeException e) {
            IrlTunes.LOGGER.debug("Cover art failed for {}: {}", t.title(), e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        cache.put(key, img);
        return img;
    }

    private byte @Nullable [] download(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        switch (scheme) {
            case "file" -> {
                Path p = Path.of(uri);
                return Files.size(p) <= MAX_BYTES ? Files.readAllBytes(p) : null;
            }
            case "http", "https" -> {
                HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
                        .header("User-Agent", "IRL-Tunes-Minecraft-Mod").GET().build();
                HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = res.body()) {
                    if (res.statusCode() != 200) return null;
                    byte[] data = in.readNBytes(MAX_BYTES + 1);
                    return data.length > MAX_BYTES ? null : data;
                }
            }
            default -> {
                return null;
            }
        }
    }

    /** iTunes Search API: free, no key. Only artist + title are sent. */
    private @Nullable String lookupItunes(String artist, String title) throws IOException, InterruptedException {
        String term = (artist + " " + title).trim();
        String url = "https://itunes.apple.com/search?media=music&entity=song&limit=1&term="
                + URLEncoder.encode(term, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
        JsonArray results = root.getAsJsonArray("results");
        if (results == null || results.isEmpty()) return null;
        JsonObject first = results.get(0).getAsJsonObject();
        if (!first.has("artworkUrl100")) return null;
        return first.get("artworkUrl100").getAsString().replace("100x100bb", SIZE * 2 + "x" + SIZE * 2 + "bb");
    }

    /** Decodes PNG/JPEG/BMP/GIF and center-crops + scales to SIZE x SIZE ARGB. */
    static @Nullable CoverImage decode(byte @Nullable [] data) throws IOException {
        if (data == null || data.length == 0) return null;
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null) return null;
        int side = Math.min(src.getWidth(), src.getHeight());
        int sx = (src.getWidth() - side) / 2, sy = (src.getHeight() - side) / 2;
        BufferedImage dst = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, SIZE, SIZE, sx, sy, sx + side, sy + side, null);
        g.dispose();
        int[] argb = dst.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
        return new CoverImage(SIZE, SIZE, argb);
    }
}
