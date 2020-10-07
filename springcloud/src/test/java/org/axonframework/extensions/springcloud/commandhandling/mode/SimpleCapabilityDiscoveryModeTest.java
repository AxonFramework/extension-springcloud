package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.junit.jupiter.api.*;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleCapabilityDiscoveryMode}.
 *
 * @author Steven van Beelen
 */
class SimpleCapabilityDiscoveryModeTest {

    private final CapabilityDiscoveryMode testSubject =
            SimpleCapabilityDiscoveryMode.builder()
                                         .restTemplate(mock(RestTemplate.class))
                                         .build();

    @Test
    void testUpdateLocalCapabilitiesDefaultsToAcceptAllCommands() {
        ServiceInstance testServiceInstance = mock(ServiceInstance.class);

        int expectedLoadFactor = 42;

        testSubject.updateLocalCapabilities(testServiceInstance, expectedLoadFactor, DenyAll.INSTANCE);
        // By using the same ServiceInstance to update the local capabilities as for searching,
        //  it is regarded as a local instance, hence allowing us to see the override of the commandFilter.
        Optional<MemberCapabilities> result = testSubject.capabilities(testServiceInstance);

        assertTrue(result.isPresent());
        MemberCapabilities resultCapabilities = result.get();
        assertEquals(expectedLoadFactor, resultCapabilities.getLoadFactor());
        assertEquals(AcceptAll.INSTANCE, resultCapabilities.getCommandFilter());
    }
}