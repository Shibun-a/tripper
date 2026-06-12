package com.embabel.tripper.safety;

import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates URLs before the app fetches them server-side (travel-knowledge imports). Without
 * this, any user could point the server at internal services or cloud metadata endpoints
 * (SSRF). A URL passes only if it is http(s), has no embedded credentials, and every address
 * its host resolves to is a public unicast address.
 */
@Service
public class UrlImportGuard {

    public void requireFetchable(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("URL is not valid: " + url);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http(s) URLs can be imported");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL has no host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs with embedded credentials are not allowed");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("URL host cannot be resolved: " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublicUnicast(address)) {
                throw new IllegalArgumentException(
                        "URL resolves to a private or internal address and cannot be imported: " + host);
            }
        }
    }

    private boolean isPublicUnicast(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() // covers 169.254.169.254 cloud metadata
                || address.isSiteLocalAddress() // 10/8, 172.16/12, 192.168/16
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 100.64.0.0/10 (carrier-grade NAT) is not covered by isSiteLocalAddress.
            return !(first == 100 && second >= 64 && second <= 127);
        }
        if (bytes.length == 16) {
            // fc00::/7: IPv6 unique-local addresses.
            return ((bytes[0] & 0xFF) & 0xFE) != 0xFC;
        }
        return false;
    }
}
