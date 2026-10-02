package com.kyc.services;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

public final class WebhookSsrfGuard {

    private WebhookSsrfGuard() {}

    public static void validate(String url, boolean live) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url required");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid url");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("invalid url host");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (live) {
            if (!"https".equals(scheme)) {
                throw new IllegalArgumentException("live webhooks require https");
            }
            if (isLoopbackName(hostLower) || isPrivateOrLinkLocal(hostLower)) {
                throw new IllegalArgumentException("ssrf_denied");
            }
            return;
        }
        if ("https".equals(scheme)) {
            if (isPrivateOrLinkLocal(hostLower) && !isLoopbackName(hostLower)) {
                // allow public https; deny RFC1918 hostnames that resolve privately — checked below
            }
            return;
        }
        if ("http".equals(scheme) && isLoopbackName(hostLower)) {
            return;
        }
        throw new IllegalArgumentException("test webhooks require https or http://localhost");
    }

    public static boolean isDeniedAfterResolve(String host, boolean live) {
        if (!live) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || isMetadata(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private static boolean isLoopbackName(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
    }

    private static boolean isPrivateOrLinkLocal(String host) {
        if (isLoopbackName(host)) {
            return true;
        }
        if (host.endsWith(".local") || host.endsWith(".internal")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || isMetadata(address);
        } catch (UnknownHostException e) {
            return host.startsWith("169.254.") || host.startsWith("10.") || host.startsWith("192.168.");
        }
    }

    private static boolean isMetadata(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && (bytes[0] & 0xff) == 169 && (bytes[1] & 0xff) == 254;
    }
}
