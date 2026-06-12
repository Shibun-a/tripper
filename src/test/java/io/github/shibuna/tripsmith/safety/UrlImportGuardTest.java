package io.github.shibuna.tripsmith.safety;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * All cases use literal IP addresses so the tests never depend on DNS.
 */
class UrlImportGuardTest {

    private final UrlImportGuard guard = new UrlImportGuard();

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("ftp://1.1.1.1/file"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("gopher://1.1.1.1/"));
    }

    @Test
    void rejectsLoopbackAndPrivateRanges() {
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://127.0.0.1/"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://10.0.0.8/x"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://192.168.1.10/"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://172.16.3.4/"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://[::1]/"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://0.0.0.0/"));
    }

    @Test
    void rejectsMetadataCgnatAndUniqueLocalAddresses() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.requireFetchable("http://169.254.169.254/latest/meta-data/"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://100.64.0.7/"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("http://[fd00::1]/"));
    }

    @Test
    void rejectsEmbeddedCredentialsAndBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("https://admin@1.1.1.1/"));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable("  "));
        assertThrows(IllegalArgumentException.class, () -> guard.requireFetchable(null));
    }

    @Test
    void allowsPublicHosts() {
        assertDoesNotThrow(() -> guard.requireFetchable("http://1.1.1.1/"));
        assertDoesNotThrow(() -> guard.requireFetchable("https://8.8.8.8/guide.html"));
    }
}
