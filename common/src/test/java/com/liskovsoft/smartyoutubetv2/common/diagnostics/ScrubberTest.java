package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScrubberTest {
    @After
    public void tearDown() {
        Scrubber.setProxyHost(null);
    }

    @Test
    public void aUrlKeepsItsHostAndPath() {
        assertEquals("Unable to connect to https://rr1---sn-abc.googlevideo.com/videoplayback?…",
                Scrubber.scrub("Unable to connect to https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&ip=203.0.113.9"));
    }

    @Test
    public void ipAddressesAreLeftOut() {
        assertEquals("failed to connect to /<ip> (port 443)", Scrubber.scrub("failed to connect to /203.0.113.9 (port 443)"));
        assertEquals("from <ip> and <ip>", Scrubber.scrub("from 2001:db8::1 and ::1"));
        assertEquals("via <ip>", Scrubber.scrub("via fe80::1%eth0"));
        assertEquals("at <ip>.", Scrubber.scrub("at 2001:0db8:85a3:0000:0000:8a2e:0370:7334."));
    }

    @Test
    public void whatOnlyLooksLikeAnAddressIsKept() {
        assertEquals("at 12:30:45", Scrubber.scrub("at 12:30:45"));
        assertEquals("std::string", Scrubber.scrub("std::string"));
        assertEquals("version 32.68.2", Scrubber.scrub("version 32.68.2"));
    }

    @Test
    public void emailsAndTheProxyAreLeftOut() {
        Scrubber.setProxyHost("Proxy.Example.org");

        assertEquals("Failed to connect to <proxy>/<ip>:3128 for <email>",
                Scrubber.scrub("Failed to connect to proxy.example.org/203.0.113.5:3128 for someone@example.com"));
    }
}
