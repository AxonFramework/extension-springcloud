package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MemberCapabilitiesController}.
 *
 * @author Steven van Beelen
 */
class MemberCapabilitiesControllerTest {

    @Test
    void testGetLocalMemberCapabilities() {
        RestCapabilityDiscoveryMode restDiscoveryMode = mock(RestCapabilityDiscoveryMode.class);
        SerializedMemberCapabilities expected = new SerializedMemberCapabilities(1, "", "");
        when(restDiscoveryMode.getLocalMemberCapabilities()).thenReturn(expected);

        MemberCapabilitiesController testSubject =
                MemberCapabilitiesController.builder()
                                            .restCapabilityDiscoveryMode(restDiscoveryMode)
                                            .build();

        SerializedMemberCapabilities result = testSubject.getLocalMemberCapabilities();

        assertEquals(expected, result);
        verify(restDiscoveryMode).getLocalMemberCapabilities();
    }

    @Test
    void testBuildWithNullRestCapabilityDiscoveryModeThrowsAxonConfigurationException() {
        MemberCapabilitiesController.Builder testSubject = MemberCapabilitiesController.builder();

        assertThrows(AxonConfigurationException.class, () -> testSubject.restCapabilityDiscoveryMode(null));
    }

    @Test
    void testBuildWithoutRestCapabilityDiscoveryModeThrowsAxonConfigurationException() {
        assertThrows(AxonConfigurationException.class, () -> MemberCapabilitiesController.builder().build());
    }
}