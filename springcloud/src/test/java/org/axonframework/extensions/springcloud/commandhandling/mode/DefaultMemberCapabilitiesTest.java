package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultMemberCapabilities}.
 *
 * @author Steven van Beelen
 */
class DefaultMemberCapabilitiesTest {

    private static final int LOAD_FACTOR = 100;
    private static final CommandMessageFilter COMMAND_FILTER = AcceptAll.INSTANCE;

    private DefaultMemberCapabilities testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_FILTER);
    }

    @Test
    void testLoadFactor() {
        assertEquals(LOAD_FACTOR, testSubject.getLoadFactor());
    }

    @Test
    void testCommandMessageFilter() {
        assertEquals(COMMAND_FILTER, testSubject.getCommandFilter());
    }
}