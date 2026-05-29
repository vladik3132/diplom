package ua.edu.teacherlicence.scopus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Scopus API integration via Elsevier Search API.
 *
 * Algorithm:
 * 1. By teacher's Scopus Author ID (AU-ID) — fetch ALL their publications from Scopus (cached).
 * 2. Match each local publication against the cached list:
 *    a) By DOI (exact match, normalized) — most reliable
 *    b) By title (fuzzy, Levenshtein ≤ threshold on normalized titles) — fallback
 * 3. If teacher has no Scopus ID — fallback to single DOI query per publication.
 *
 * This approach: 1 API call per teacher (paginated) instead of N calls per publication.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "scopus.enabled", havingValue = "true")
public class ScopusApiService {

    private static final String BASE_URL = "https://api.elsevier.com/content/search/scopus";
    /** Max Levenshtein distance for title matching (relative to title length) */
    private static final double TITLE_DISTANCE_RATIO = 0.15; // 15% of title length

    @Value("${scopus.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Semaphore rateLimiter = new Semaphore(3);

    /** Cache: Scopus Author ID → list of their Scopus publications */
    private final Map<String, List<ScopusPublication>> authorCache = new ConcurrentHashMap<>();

    /** Cache: DOI → ScopusPublication (for teachers without Scopus ID) */
    private final Map<String, ScopusPublication> doiCache = new ConcurrentHashMap<>();

    // ─── Inner record for cached Scopus publications ────────────

    public record ScopusPublication(
            String eid,
            String title,
            String doi,
            String normalizedTitle,
            String normalizedDoi
    ) {
        static ScopusPublication from(JsonNode entry) {
            String title = getFieldStatic(entry, "dc:title");
            String doi = getFieldStatic(entry, "prism:doi");
            String eid = getFieldStatic(entry, "eid");
            return new ScopusPublication(
                    eid,
                    title,
                    doi,
                    normalizeTitle(title),
                    normalizeDoi(doi)
            );
        }
    }

    // ─── Main verification method ───────────────────────────────

    /**
     * Verify that a publication exists in Scopus for the given teacher.
     *
     * @param title             Publication title
     * @param doi               Publication DOI (may be null)
     * @param scopusAuthorId    Teacher's Scopus Author ID (may be null)
     * @param teacherLastName   For logging only (not used for matching)
     * @param teacherFirstName  For logging only
     */
    public ScopusVerificationResult verifyPublication(
            String title, String doi,
            String scopusAuthorId,
            String teacherLastName, String teacherFirstName
    ) {
        if (title == null || title.isBlank()) {
            return ScopusVerificationResult.notFound();
        }

        // ── Strategy 1: Teacher has Scopus Author ID → fetch all their publications ──
        if (scopusAuthorId != null && !scopusAuthorId.isBlank()) {
            List<ScopusPublication> authorPubs = getAuthorPublications(scopusAuthorId.trim());

            if (authorPubs != null && !authorPubs.isEmpty()) {
                // Try DOI match first (most reliable)
                if (doi != null && !doi.isBlank()) {
                    String normalizedDoi = normalizeDoi(doi);
                    for (ScopusPublication sp : authorPubs) {
                        if (sp.normalizedDoi != null && sp.normalizedDoi.equals(normalizedDoi)) {
                            log.info("Scopus MATCH by DOI for '{}' (AU-ID: {}, EID: {})",
                                    truncate(title, 50), scopusAuthorId, sp.eid);
                            return ScopusVerificationResult.builder()
                                    .found(true)
                                    .authorConfirmed(true)
                                    .scopusId(sp.eid)
                                    .scopusTitle(sp.title)
                                    .searchMethod("AU-ID+DOI")
                                    .build();
                        }
                    }
                }

                // Try title match (fuzzy)
                String normalizedTitle = normalizeTitle(title);
                ScopusPublication bestMatch = null;
                int bestDistance = Integer.MAX_VALUE;

                for (ScopusPublication sp : authorPubs) {
                    if (sp.normalizedTitle == null) continue;
                    int distance = levenshtein(normalizedTitle, sp.normalizedTitle);
                    int maxAllowed = Math.max(3, (int) (normalizedTitle.length() * TITLE_DISTANCE_RATIO));
                    if (distance <= maxAllowed && distance < bestDistance) {
                        bestDistance = distance;
                        bestMatch = sp;
                    }
                }

                if (bestMatch != null) {
                    log.info("Scopus MATCH by TITLE for '{}' ↔ '{}' (distance={}, AU-ID: {}, EID: {})",
                            truncate(title, 40), truncate(bestMatch.title, 40),
                            bestDistance, scopusAuthorId, bestMatch.eid);
                    return ScopusVerificationResult.builder()
                            .found(true)
                            .authorConfirmed(true)
                            .scopusId(bestMatch.eid)
                            .scopusTitle(bestMatch.title)
                            .searchMethod("AU-ID+TITLE")
                            .build();
                }

                log.debug("Scopus: no match for '{}' among {} author publications (AU-ID: {})",
                        truncate(title, 50), authorPubs.size(), scopusAuthorId);
                return ScopusVerificationResult.notFound();
            }
        }

        // ── Strategy 2: No Scopus ID → single DOI lookup ──
        if (doi != null && !doi.isBlank()) {
            return searchByDoi(doi);
        }

        // No Scopus ID and no DOI — cannot verify
        return ScopusVerificationResult.notFound();
    }

    // ─── Fetch all publications by Author ID ────────────────────

    /**
     * Fetch all Scopus publications for a given author ID.
     * Results are cached — one API call (paginated) per teacher per session.
     */
    private List<ScopusPublication> getAuthorPublications(String scopusAuthorId) {
        return authorCache.computeIfAbsent(scopusAuthorId, id -> {
            log.info("Scopus: fetching publications for AU-ID: {}", id);
            List<ScopusPublication> allPubs = new ArrayList<>();
            int start = 0;
            int pageSize = 25; // Scopus max per page
            int totalResults = -1;

            while (true) {
                String query = "AU-ID(" + id + ")";
                JsonNode results = executeSearchRaw(query, start, pageSize);
                if (results == null) break;

                if (totalResults < 0) {
                    totalResults = results.path("opensearch:totalResults").asInt(0);
                    log.info("Scopus: AU-ID {} has {} total publications", id, totalResults);
                    if (totalResults == 0) break;
                }

                JsonNode entries = results.path("entry");
                if (!entries.isArray() || entries.isEmpty()) break;

                for (JsonNode entry : entries) {
                    if (entry.has("error")) continue; // skip error markers
                    allPubs.add(ScopusPublication.from(entry));
                }

                start += pageSize;
                if (start >= totalResults) break;
                // Safety: max 400 publications (16 pages)
                if (start > 400) {
                    log.warn("Scopus: AU-ID {} has >400 publications, truncating", id);
                    break;
                }
            }

            log.info("Scopus: fetched {} publications for AU-ID: {}", allPubs.size(), id);
            return allPubs;
        });
    }

    // ─── Fallback: DOI-only search (no Author ID) ───────────────

    private ScopusVerificationResult searchByDoi(String doi) {
        String normalizedDoi = normalizeDoi(doi);
        // Check DOI cache first
        ScopusPublication cached = doiCache.get(normalizedDoi);
        if (cached != null) {
            return ScopusVerificationResult.builder()
                    .found(true)
                    .authorConfirmed(true) // DOI is unique → if found, it's the right article
                    .scopusId(cached.eid)
                    .scopusTitle(cached.title)
                    .searchMethod("DOI")
                    .build();
        }

        String query = "DOI(" + cleanDoi(doi) + ")";
        JsonNode results = executeSearchRaw(query, 0, 1);
        if (results == null) return ScopusVerificationResult.notFound();

        int total = results.path("opensearch:totalResults").asInt(0);
        if (total == 0) return ScopusVerificationResult.notFound();

        JsonNode entries = results.path("entry");
        if (!entries.isArray() || entries.isEmpty()) return ScopusVerificationResult.notFound();

        JsonNode first = entries.get(0);
        if (first.has("error")) return ScopusVerificationResult.notFound();

        ScopusPublication sp = ScopusPublication.from(first);
        doiCache.put(normalizedDoi, sp);

        log.info("Scopus: found by DOI: '{}' (EID: {})", truncate(sp.title, 50), sp.eid);
        // DOI match = article confirmed in Scopus. Author confirmed because DOI is unique.
        return ScopusVerificationResult.builder()
                .found(true)
                .authorConfirmed(true)
                .scopusId(sp.eid)
                .scopusTitle(sp.title)
                .searchMethod("DOI")
                .build();
    }

    // ─── HTTP / API ─────────────────────────────────────────────

    /**
     * Execute a Scopus search query and return the raw "search-results" node.
     */
    private JsonNode executeSearchRaw(String query, int start, int count) {
        try {
            rateLimiter.acquire();
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = BASE_URL + "?query=" + encoded
                        + "&start=" + start
                        + "&count=" + count
                        + "&view=STANDARD"
                        + "&sort=pubyear"; // newest first

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("X-ELS-APIKey", apiKey)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                log.debug("Scopus API: {} (start={}, count={})", query, start, count);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    log.error("Scopus API auth error ({}). Check API key.", response.statusCode());
                    return null;
                }
                if (response.statusCode() == 429) {
                    log.warn("Scopus API rate limit exceeded (429). Waiting...");
                    Thread.sleep(1000);
                    return null;
                }
                if (response.statusCode() != 200) {
                    log.warn("Scopus API returned {}: {}", response.statusCode(),
                            response.body().length() > 300 ? response.body().substring(0, 300) : response.body());
                    return null;
                }

                JsonNode root = objectMapper.readTree(response.body());
                return root.path("search-results");

            } finally {
                Thread.sleep(350); // ~3 req/sec rate limiting
                rateLimiter.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Scopus API interrupted");
            return null;
        } catch (Exception e) {
            log.error("Scopus API error: {}", e.getMessage());
            return null;
        }
    }

    // ─── Normalization helpers ──────────────────────────────────

    /**
     * Normalize title for comparison: lowercase, remove punctuation, collapse spaces.
     */
    static String normalizeTitle(String title) {
        if (title == null) return null;
        return title.toLowerCase()
                .replaceAll("[^a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9\\s]", "") // keep letters + digits + spaces
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Normalize DOI for comparison: lowercase, strip URL prefixes.
     */
    static String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) return null;
        return doi.toLowerCase()
                .replace("https://doi.org/", "")
                .replace("http://doi.org/", "")
                .replace("https://dx.doi.org/", "")
                .replace("http://dx.doi.org/", "")
                .replace("doi:", "")
                .trim();
    }

    private String cleanDoi(String doi) {
        return doi.replace("https://doi.org/", "")
                .replace("http://doi.org/", "")
                .replace("https://dx.doi.org/", "")
                .replace("http://dx.doi.org/", "")
                .replace("doi:", "")
                .trim();
    }

    private static String getFieldStatic(JsonNode node, String field) {
        JsonNode val = node.get(field);
        return (val != null && !val.isNull()) ? val.asText() : null;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * Levenshtein distance (case-insensitive).
     */
    static int levenshtein(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }

    public void clearCache() {
        authorCache.clear();
        doiCache.clear();
    }
}
