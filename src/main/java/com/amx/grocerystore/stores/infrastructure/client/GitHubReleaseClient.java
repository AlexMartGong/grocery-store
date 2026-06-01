package com.amx.grocerystore.stores.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GitHubReleaseClient {

    private static final String LATEST_RELEASE_URI =
            "https://api.github.com/repos/AlexMartGong/pos-agent/releases/latest";
    private static final String SHA256_ASSET_NAME = "sha256.txt";
    private static final String JAR_SUFFIX = ".jar";
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-fA-F0-9]{64}");

    private final RestClient restClient;

    public GitHubReleaseClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "pos-agent-update-checker")
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    public Optional<GitHubRelease> fetchLatestRelease() {
        try {
            GitHubReleaseResponse response = restClient.get()
                    .uri(LATEST_RELEASE_URI)
                    .retrieve()
                    .body(GitHubReleaseResponse.class);

            if (response == null || response.tagName() == null || response.tagName().isBlank()) {
                log.warn("GitHub latest release returned no usable tag_name");
                return Optional.empty();
            }

            String downloadUrl = resolveJarDownloadUrl(response.assets());
            String sha256 = resolveSha256(response.assets(), response.body());

            return Optional.of(new GitHubRelease(response.tagName(), downloadUrl, sha256));
        } catch (Exception e) {
            log.error("Failed to fetch latest pos-agent release from GitHub: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveJarDownloadUrl(List<Asset> assets) {
        if (assets == null) {
            return null;
        }
        return assets.stream()
                .filter(a -> a.name() != null && a.name().endsWith(JAR_SUFFIX))
                .map(Asset::browserDownloadUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String resolveSha256(List<Asset> assets, String body) {
        String fromAsset = sha256FromAsset(assets);
        if (fromAsset != null) {
            return fromAsset;
        }
        return extractSha256(body);
    }

    private String sha256FromAsset(List<Asset> assets) {
        if (assets == null) {
            return null;
        }
        String sha256Url = assets.stream()
                .filter(a -> SHA256_ASSET_NAME.equalsIgnoreCase(a.name()))
                .map(Asset::browserDownloadUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
        if (sha256Url == null) {
            return null;
        }
        try {
            String content = restClient.get()
                    .uri(sha256Url)
                    .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .body(String.class);
            return extractSha256(content);
        } catch (Exception e) {
            log.error("Failed to fetch sha256.txt asset: {}", e.getMessage());
            return null;
        }
    }

    private String extractSha256(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = SHA256_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    public record GitHubRelease(String latestVersion, String downloadUrl, String sha256) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubReleaseResponse(
            @JsonProperty("tag_name") String tagName,
            String body,
            List<Asset> assets
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Asset(
            String name,
            @JsonProperty("browser_download_url") String browserDownloadUrl
    ) {
    }
}
