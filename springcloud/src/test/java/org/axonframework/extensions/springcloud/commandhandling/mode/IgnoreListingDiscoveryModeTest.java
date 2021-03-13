package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.junit.jupiter.api.*;
import org.springframework.cloud.client.ServiceInstance;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link IgnoreListingDiscoveryMode}.
 *
 * @author Steven van Beelen
 */
class IgnoreListingDiscoveryModeTest {

    private static final int LOAD_FACTOR = 42;
    private static final CommandMessageFilter COMMAND_MESSAGE_FILTER = AcceptAll.INSTANCE;

    private final CapabilityDiscoveryMode delegate = mock(CapabilityDiscoveryMode.class);

    private final IgnoreListingDiscoveryMode testSubject = IgnoreListingDiscoveryMode.builder()
                                                                                     .delegate(delegate)
                                                                                     .build();

    private final ServiceInstance testServiceInstance = mock(ServiceInstance.class);

    @Test
    void testUpdateLocalCapabilitiesInvokesDelegateCapabilityDiscoveryMode() {
        testSubject.updateLocalCapabilities(testServiceInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        verify(delegate).updateLocalCapabilities(testServiceInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);
    }

    @Test
    void testCapabilitiesInvokesDelegateCapabilityDiscoveryMode() {
        MemberCapabilities expectedCapabilities = new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);
        when(delegate.capabilities(testServiceInstance)).thenReturn(Optional.of(expectedCapabilities));

        Optional<MemberCapabilities> result = testSubject.capabilities(testServiceInstance);

        verify(delegate).capabilities(testServiceInstance);

        assertTrue(result.isPresent());
        assertEquals(expectedCapabilities, result.get());
    }

    @Test
    void testCapabilitiesReturnsEmptyOptionalWhenDelegateThrowsServiceInstanceClientException() {
        when(delegate.capabilities(testServiceInstance)).thenThrow(ServiceInstanceClientException.class);

        Optional<MemberCapabilities> result = testSubject.capabilities(testServiceInstance);

        assertFalse(result.isPresent());
    }

    @Test
    void testCapabilitiesReturnsEmptyOptionalForIgnoredService() {
        when(delegate.capabilities(testServiceInstance)).thenThrow(ServiceInstanceClientException.class)
                                                        .thenReturn(Optional.of(mock(MemberCapabilities.class)));

        // First invocation makes it so that the Service Instance is ignored
        testSubject.capabilities(testServiceInstance);

        Optional<MemberCapabilities> result = testSubject.capabilities(testServiceInstance);

        assertFalse(result.isPresent());
    }
}