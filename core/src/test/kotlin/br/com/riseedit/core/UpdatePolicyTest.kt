package br.com.riseedit.core

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun numericVersionComparisonPreventsDowngradesAndPrereleases() {
        assertTrue(UpdatePolicy.newer("v0.10.0", "0.9.9"))
        assertFalse(UpdatePolicy.newer("v0.1.0", "0.1.0"))
        assertFalse(UpdatePolicy.newer("v0.0.9", "0.1.0"))
        assertFalse(UpdatePolicy.newer("v0.2.0-beta", "0.1.0"))
        assertFalse(UpdatePolicy.newer("broken", "0.1.0"))
    }
    @Test fun acceptsOnlySha256Digest() {
        assertEquals("a".repeat(64), UpdatePolicy.digest("sha256:" + "A".repeat(64)))
        assertNull(UpdatePolicy.digest("sha256:abcd"))
        assertNull(UpdatePolicy.digest("md5:" + "a".repeat(64)))
    }
}
