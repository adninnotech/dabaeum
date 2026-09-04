package com.adn.dabaeum.credential.config;

import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dabaeum.vc")
public record VcProperties(
    String publicBaseUrl,
    String contextVersion,
    String keyVersion
) {

    private static final String SUPPORTED_CONTEXT_VERSION = "v1";
    private static final Pattern KEY_VERSION =
        Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    public VcProperties {
        publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        if (!SUPPORTED_CONTEXT_VERSION.equals(contextVersion)) {
            throw new IllegalArgumentException("VC context version is invalid");
        }
        if (keyVersion == null || !KEY_VERSION.matcher(keyVersion).matches()) {
            throw new IllegalArgumentException("VC key version is invalid");
        }
    }

    private static String normalizeBaseUrl(String value) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("VC public base URL is required");
            }
            URI uri = URI.create(value.trim());
            if (!uri.isAbsolute()
                || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("VC public base URL is invalid");
            }
            String normalized = uri.toString();
            return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                && exception.getMessage() != null
                && exception.getMessage().startsWith("VC public base URL")) {
                throw exception;
            }
            throw new IllegalArgumentException("VC public base URL is invalid");
        }
    }
}
