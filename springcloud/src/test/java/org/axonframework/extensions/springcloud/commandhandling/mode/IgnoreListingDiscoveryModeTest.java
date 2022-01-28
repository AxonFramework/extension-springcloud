/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;
import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
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

    @Test
    void testCapabilitiesEvictsServiceInstancesAfterTheExpireThresholdIsReached() {
        int testExpireThresholdMs = 5000;
        IgnoreListingDiscoveryMode testSubjectWithCustomExpireThreshold =
                IgnoreListingDiscoveryMode.builder()
                                          .delegate(delegate)
                                          .expireThreshold(Duration.ofMillis(testExpireThresholdMs))
                                          .build();

        ServiceInstance testInstanceOne = new StubServiceInstance("idOne", "hostOne", 1);
        ServiceInstance testInstanceTwo = new StubServiceInstance("idTwo", "hostTwo", 2);
        ServiceInstance testInstanceThree = new StubServiceInstance("idThree", "hostThree", 3);
        ServiceInstance testInstanceFour = new StubServiceInstance("idFour", "hostFour", 4);
        MemberCapabilities expectedCapabilities = new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        when(delegate.capabilities(testInstanceOne)).thenThrow(ServiceInstanceClientException.class)
                                                    .thenReturn(Optional.of(expectedCapabilities));
        when(delegate.capabilities(testInstanceTwo)).thenThrow(ServiceInstanceClientException.class)
                                                    .thenReturn(Optional.of(expectedCapabilities));
        when(delegate.capabilities(testInstanceThree)).thenThrow(ServiceInstanceClientException.class)
                                                      .thenReturn(Optional.of(expectedCapabilities));
        when(delegate.capabilities(testInstanceFour)).thenReturn(Optional.of(expectedCapabilities));

        Instant now = Instant.now();
        IgnoreListingDiscoveryMode.clock = Clock.fixed(now, ZoneId.of("UTC"));
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceOne).isPresent());
        // the second invocation would still net an empty optional, as the ServiceInstance is ignored
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceOne).isPresent());

        IgnoreListingDiscoveryMode.clock = Clock.fixed(now.plusMillis(testExpireThresholdMs / 2), ZoneId.of("UTC"));
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceTwo).isPresent());
        // we adjusted the clock, but both instance one and two are still ignored as the threshold isn't met
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceOne).isPresent());
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceTwo).isPresent());


        IgnoreListingDiscoveryMode.clock = Clock.fixed(now.plusMillis(testExpireThresholdMs - 1), ZoneId.of("UTC"));
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceThree).isPresent());
        // we adjusted the clock more, but instance's one, two, and three are still ignored as the threshold isn't met
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceOne).isPresent());
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceTwo).isPresent());
        assertFalse(testSubjectWithCustomExpireThreshold.capabilities(testInstanceThree).isPresent());


        IgnoreListingDiscoveryMode.clock = Clock.fixed(now.plusMillis(testExpireThresholdMs * 2), ZoneId.of("UTC"));
        // instance four was not ignored to begin with through mocking...
        Optional<MemberCapabilities> resultFour = testSubjectWithCustomExpireThreshold.capabilities(testInstanceFour);
        assertTrue(resultFour.isPresent());
        assertEquals(expectedCapabilities, resultFour.get());

        // ...but it has also reintroduced instance's one, two, and three for evaluation
        Optional<MemberCapabilities> resultOne = testSubjectWithCustomExpireThreshold.capabilities(testInstanceOne);
        assertTrue(resultOne.isPresent());
        assertEquals(expectedCapabilities, resultOne.get());

        Optional<MemberCapabilities> resultTwo = testSubjectWithCustomExpireThreshold.capabilities(testInstanceTwo);
        assertTrue(resultTwo.isPresent());
        assertEquals(expectedCapabilities, resultTwo.get());

        Optional<MemberCapabilities> resultThree = testSubjectWithCustomExpireThreshold.capabilities(testInstanceThree);
        assertTrue(resultThree.isPresent());
        assertEquals(expectedCapabilities, resultThree.get());
    }

    @Test
    void testBuildWithNegativeOrZeroExpireThresholdThrowsAxonConfigurationException() {
        IgnoreListingDiscoveryMode.Builder builderTestSubject = IgnoreListingDiscoveryMode.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.expireThreshold(Duration.ofMinutes(-1)));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.expireThreshold(Duration.ZERO));
    }

    private static class StubServiceInstance implements ServiceInstance {

        private final String serviceId;
        private final String host;
        private final int port;

        private StubServiceInstance(String serviceId, String host, int port) {
            this.serviceId = serviceId;
            this.host = host;
            this.port = port;
        }

        @Override
        public String getServiceId() {
            return serviceId;
        }

        @Override
        public String getHost() {
            return host;
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public URI getUri() {
            return null;
        }

        @Override
        public Map<String, String> getMetadata() {
            return null;
        }
    }
}