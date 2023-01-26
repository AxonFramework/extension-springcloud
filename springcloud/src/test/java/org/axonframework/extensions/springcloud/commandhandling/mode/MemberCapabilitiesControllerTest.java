/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        MemberCapabilitiesController.Builder testSubject = MemberCapabilitiesController.builder();

        assertThrows(AxonConfigurationException.class, testSubject::build);
    }
}