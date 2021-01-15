package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;
import org.springframework.cloud.client.ServiceInstance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AcceptAllCommandsDiscoveryMode}.
 *
 * @author Steven van Beelen
 */
class AcceptAllCommandsDiscoveryModeTest {

    private final CapabilityDiscoveryMode delegate = mock(CapabilityDiscoveryMode.class);

    private final CapabilityDiscoveryMode testSubject = AcceptAllCommandsDiscoveryMode.builder()
                                                                                      .delegate(delegate)
                                                                                      .build();

    @Test
    void testUpdateLocalCapabilitiesDefaultsToAcceptAllCommands() {
        ServiceInstance expectedServiceInstance = mock(ServiceInstance.class);
        int expectedLoadFactor = 42;

        testSubject.updateLocalCapabilities(expectedServiceInstance, expectedLoadFactor, DenyAll.INSTANCE);

        verify(delegate).updateLocalCapabilities(expectedServiceInstance, expectedLoadFactor, AcceptAll.INSTANCE);
    }

    @Test
    void testCapabilitiesIsDelegated() {
        ServiceInstance testServiceInstance = mock(ServiceInstance.class);

        testSubject.capabilities(testServiceInstance);

        verify(delegate).capabilities(testServiceInstance);
    }

    @Test
    void testBuildWithNullDelegateThrowsAxonConfigurationException() {
        AcceptAllCommandsDiscoveryMode.Builder builderTestSubject = AcceptAllCommandsDiscoveryMode.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.delegate(null));
    }

    @Test
    void testBuildWithoutDelegateThrowsAxonConfigurationException() {
        AcceptAllCommandsDiscoveryMode.Builder builderTestSubject = AcceptAllCommandsDiscoveryMode.builder();

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }
}