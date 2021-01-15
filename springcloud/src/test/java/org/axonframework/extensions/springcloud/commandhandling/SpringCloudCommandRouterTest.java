/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.springcloud.commandhandling;

import com.google.common.collect.ImmutableList;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.ConsistentHashChangeListener;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.DefaultMemberCapabilities;
import org.axonframework.extensions.springcloud.commandhandling.mode.MemberCapabilities;
import org.axonframework.extensions.springcloud.commandhandling.utils.TestSerializer;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SpringCloudCommandRouter}.
 *
 * @author Steven van Beelen
 */
class SpringCloudCommandRouterTest {

    private static final int LOAD_FACTOR = 1337;
    private static final CommandMessageFilter COMMAND_MESSAGE_FILTER = AcceptAll.INSTANCE;
    private static final MemberCapabilities DEFAULT_MEMBER_CAPABILITIES =
            new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);
    private static final String SERVICE_INSTANCE_ID = "SERVICE_ID";
    private static final URI SERVICE_INSTANCE_URI = URI.create("endpoint");
    private static final String CONTEXT_ROOT_KEY = "contextRoot";

    private static final boolean REGISTERED = true;
    private static final boolean NOT_REGISTERED = false;

    private final DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
    private final Registration localServiceInstance = mock(Registration.class);
    private final RoutingStrategy routingStrategy = mock(RoutingStrategy.class);
    private final CapabilityDiscoveryMode capabilityDiscoveryMode = mock(CapabilityDiscoveryMode.class);
    private final ConsistentHashChangeListener consistentHashChangeListener = mock(ConsistentHashChangeListener.class);
    private final Serializer serializer = TestSerializer.secureXStreamSerializer();

    private SpringCloudCommandRouter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = SpringCloudCommandRouter.builder()
                                              .discoveryClient(discoveryClient)
                                              .localServiceInstance(localServiceInstance)
                                              .routingStrategy(routingStrategy)
                                              .capabilityDiscoveryMode(capabilityDiscoveryMode)
                                              .serializer(serializer)
                                              .consistentHashChangeListener(consistentHashChangeListener)
                                              .build();
    }

    @Test
    void testFindDestinationReturnsEmptyOptionalMemberForCommandMessage() {
        String routingKey = "routingKey";
        CommandMessage<Object> testCommand = GenericCommandMessage.asCommandMessage("testCommand");

        when(routingStrategy.getRoutingKey(any())).thenReturn(routingKey);

        Optional<Member> result = testSubject.findDestination(testCommand);

        assertFalse(result.isPresent());
        verify(routingStrategy).getRoutingKey(testCommand);
    }

    @Test
    void testFindDestinationReturnsMemberForCommandMessage() {
        String routingKey = "routingKey";
        CommandMessage<Object> testCommand = GenericCommandMessage.asCommandMessage("testCommand");

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(routingStrategy.getRoutingKey(any())).thenReturn(routingKey);

        // Set local service instance in the ConsistentHash
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        Optional<Member> resultOptional = testSubject.findDestination(testCommand);

        assertTrue(resultOptional.isPresent());
        Member resultMember = resultOptional.orElseThrow(IllegalStateException::new);

        assertLocalMember(resultMember, NOT_REGISTERED);

        verify(routingStrategy).getRoutingKey(testCommand);
    }

    @Test
    void testUpdateMembershipUpdatesLocalCapabilities() {
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);
        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);

        testSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        verify(capabilityDiscoveryMode).updateLocalCapabilities(
                localServiceInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER
        );
        verify(localServiceInstance).getServiceId();
        verify(consistentHashChangeListener).onConsistentHashChanged(consistentHashCaptor.capture());

        ConsistentHash resultHash = consistentHashCaptor.getValue();
        Set<Member> resultMembers = resultHash.getMembers();
        assertFalse(resultMembers.isEmpty());
        assertLocalMember(resultMembers.iterator().next(), NOT_REGISTERED);
    }

    // Reproduces issue #1 (https://github.com/AxonFramework/extension-springcloud/issues/1)
    @Test
    void testResetLocalMembershipWithoutAnyUpdateMembershipInvocations() {
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localServiceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(discoveryClient.getServices()).thenReturn(singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(singletonList(localServiceInstance));
        when(capabilityDiscoveryMode.capabilities(localServiceInstance))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));

        // When
        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(capabilityDiscoveryMode).capabilities(localServiceInstance);
        verify(localServiceInstance).getServiceId();
        verify(consistentHashChangeListener).onConsistentHashChanged(consistentHashCaptor.capture());

        ConsistentHash resultAfterInstanceRegisteredEvent = consistentHashCaptor.getValue();
        Set<Member> resultMembersAfter = resultAfterInstanceRegisteredEvent.getMembers();
        assertFalse(resultMembersAfter.isEmpty());
        assertLocalMember(resultMembersAfter.iterator().next(), REGISTERED);
    }

    @Test
    void testResetLocalMemberUpdatesConsistentHashByReplacingLocalMember() {
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localServiceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(discoveryClient.getServices()).thenReturn(singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(singletonList(localServiceInstance));
        when(capabilityDiscoveryMode.capabilities(localServiceInstance))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));
        // Start up command router
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        // When
        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));

        // Then
        verify(capabilityDiscoveryMode).updateLocalCapabilities(
                localServiceInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER
        );
        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(capabilityDiscoveryMode).capabilities(localServiceInstance);
        // Invoked twice because of SpringCloudCommandRouter#updateMembership set-up invocation
        verify(localServiceInstance, times(2)).getServiceId();
        verify(consistentHashChangeListener, times(2)).onConsistentHashChanged(consistentHashCaptor.capture());

        ConsistentHash resultPriorToInstanceRegisteredEvent = consistentHashCaptor.getAllValues().get(0);
        Set<Member> resultMembersPrior = resultPriorToInstanceRegisteredEvent.getMembers();
        assertFalse(resultMembersPrior.isEmpty());
        assertLocalMember(resultMembersPrior.iterator().next(), NOT_REGISTERED);

        ConsistentHash resultAfterInstanceRegisteredEvent = consistentHashCaptor.getAllValues().get(1);
        Set<Member> resultMembersAfter = resultAfterInstanceRegisteredEvent.getMembers();
        assertFalse(resultMembersAfter.isEmpty());
        assertLocalMember(resultMembersAfter.iterator().next(), REGISTERED);
    }

    @Test
    void testUpdateMembershipsOnHeartbeatEventUpdatesConsistentHash() {
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localServiceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(discoveryClient.getServices()).thenReturn(singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(singletonList(localServiceInstance));
        when(capabilityDiscoveryMode.capabilities(localServiceInstance))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));

        // Start up command router
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);
        // Set command router has passed the start up phase through InstanceRegisteredEvent
        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));

        // When
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        // Then, invoked twice because of SpringCloudCommandRouter#resetLocalMembership set-up invocation
        verify(discoveryClient, times(2)).getServices();
        verify(discoveryClient, times(2)).getInstances(SERVICE_INSTANCE_ID);
        verify(capabilityDiscoveryMode, times(2)).capabilities(localServiceInstance);
        // Invoked thrice because of SpringCloudCommandRouter#updateMembership and resetLocalMembership set-up
        verify(localServiceInstance, times(3)).getServiceId();
        verify(consistentHashChangeListener, times(3)).onConsistentHashChanged(consistentHashCaptor.capture());

        ConsistentHash resultAfterInstanceRegisteredEvent = consistentHashCaptor.getAllValues().get(2);
        Set<Member> resultMembersAfter = resultAfterInstanceRegisteredEvent.getMembers();
        assertFalse(resultMembersAfter.isEmpty());
        assertLocalMember(resultMembersAfter.iterator().next(), REGISTERED);
    }

    @Test
    void testUpdateMembershipAfterHeartbeatEventDoesNotOverwriteMembers() {
        int expectedRemoteLoadFactor = 50;
        CommandMessageFilter expectedRemoteCommandFilter = commandMessage -> true;
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);

        String remoteServiceId = SERVICE_INSTANCE_ID + "-1";
        URI remoteUri = URI.create("remote");
        ServiceInstance remoteServiceInstance = mock(ServiceInstance.class);
        when(remoteServiceInstance.getServiceId()).thenReturn(remoteServiceId);
        when(remoteServiceInstance.getUri()).thenReturn(remoteUri);

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localServiceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, remoteServiceId));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(singletonList(localServiceInstance));
        when(discoveryClient.getInstances(remoteServiceId)).thenReturn(singletonList(remoteServiceInstance));
        when(capabilityDiscoveryMode.capabilities(localServiceInstance))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));
        when(capabilityDiscoveryMode.capabilities(remoteServiceInstance)).thenReturn(Optional.of(
                new DefaultMemberCapabilities(expectedRemoteLoadFactor, expectedRemoteCommandFilter)
        ));
        // First invoke updateMemberships
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        // When invoking updateMembership, the ConsistentHash should've stayed the same
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        // Then
        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(discoveryClient).getInstances(remoteServiceId);
        verify(capabilityDiscoveryMode).capabilities(localServiceInstance);
        verify(capabilityDiscoveryMode).capabilities(remoteServiceInstance);
        verify(consistentHashChangeListener, times(2)).onConsistentHashChanged(consistentHashCaptor.capture());

        ConsistentHash resultPriorToUpdateMembershipInvocation = consistentHashCaptor.getAllValues().get(0);
        Set<Member> resultMembersPrior = resultPriorToUpdateMembershipInvocation.getMembers();
        assertEquals(2, resultMembersPrior.size());
        for (Member resultMember : resultMembersPrior) {
            if (resultMember.name().contains("remote")) {
                assertEquals(remoteServiceId + "[" + remoteUri + "]", resultMember.name());
                ConsistentHash.ConsistentHashMember resultHashMember = (ConsistentHash.ConsistentHashMember) resultMember;
                assertEquals(expectedRemoteLoadFactor, resultHashMember.segmentCount());
                assertEquals(expectedRemoteCommandFilter, resultHashMember.getCommandFilter());
                Optional<URI> optionalResultEndpoint = resultHashMember.getConnectionEndpoint(URI.class);
                assertTrue(optionalResultEndpoint.isPresent());
                URI resultEndpoint = optionalResultEndpoint.orElseThrow(IllegalStateException::new);
                assertEquals(resultEndpoint, remoteUri);
            } else {
                // Local member is NOT_REGISTERED since SpringCloudCommandRouter#resetLocalMembership is not invoked.
                assertLocalMember(resultMember, NOT_REGISTERED);
            }
        }

        ConsistentHash resultAfterUpdateMembershipInvocation = consistentHashCaptor.getAllValues().get(1);
        Set<Member> resultMembersAfter = resultAfterUpdateMembershipInvocation.getMembers();
        assertEquals(2, resultMembersAfter.size());
        for (Member resultMember : resultMembersAfter) {
            if (resultMember.name().contains("remote")) {
                assertEquals(remoteServiceId + "[" + remoteUri + "]", resultMember.name());
                ConsistentHash.ConsistentHashMember resultHashMember = (ConsistentHash.ConsistentHashMember) resultMember;
                assertEquals(expectedRemoteLoadFactor, resultHashMember.segmentCount());
                assertEquals(expectedRemoteCommandFilter, resultHashMember.getCommandFilter());
                Optional<URI> optionalResultEndpoint = resultHashMember.getConnectionEndpoint(URI.class);
                assertTrue(optionalResultEndpoint.isPresent());
                URI resultEndpoint = optionalResultEndpoint.orElseThrow(IllegalStateException::new);
                assertEquals(resultEndpoint, remoteUri);
            } else {
                assertLocalMember(resultMember, NOT_REGISTERED);
            }
        }
    }

    @Test
    void testUpdateMembershipsWithVanishedMemberOnHeartbeatEventRemovesMember() {
        int expectedRemoteLoadFactor = 50;
        CommandMessageFilter expectedRemoteCommandFilter = commandMessage -> true;
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);

        String remoteServiceId = SERVICE_INSTANCE_ID + "-1";
        ServiceInstance remoteServiceInstance = mock(ServiceInstance.class);
        when(remoteServiceInstance.getUri()).thenReturn(URI.create("remote"));
        when(remoteServiceInstance.getServiceId()).thenReturn(remoteServiceId);

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localServiceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, remoteServiceId));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(singletonList(localServiceInstance));
        when(discoveryClient.getInstances(remoteServiceId)).thenReturn(singletonList(remoteServiceInstance));
        when(capabilityDiscoveryMode.capabilities(localServiceInstance))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));
        when(capabilityDiscoveryMode.capabilities(remoteServiceInstance)).thenReturn(Optional.of(
                new DefaultMemberCapabilities(expectedRemoteLoadFactor, expectedRemoteCommandFilter)
        ));

        // Start up command router
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);
        // Set command router has passed the start up phase through InstanceRegisteredEvent
        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));

        // When
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        // Then, invoked twice because of SpringCloudCommandRouter#resetLocalMembership set-up invocation
        verify(discoveryClient, times(2)).getServices();
        verify(discoveryClient, times(2)).getInstances(SERVICE_INSTANCE_ID);
        verify(discoveryClient, times(2)).getInstances(remoteServiceId);
        verify(capabilityDiscoveryMode, times(2)).capabilities(localServiceInstance);
        verify(capabilityDiscoveryMode, times(2)).capabilities(remoteServiceInstance);

        // Evict remote service instance from discovery client and update router memberships
        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID));
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        verify(consistentHashChangeListener, times(4)).onConsistentHashChanged(consistentHashCaptor.capture());

        // First HeartbeatEvent invocation is third ConsistentHashChangeListener invocation due to set-up
        ConsistentHash resultPriorToVanishedInstance = consistentHashCaptor.getAllValues().get(2);
        assertEquals(2, resultPriorToVanishedInstance.getMembers().size());

        ConsistentHash resultAfterVanishedInstance = consistentHashCaptor.getAllValues().get(3);
        Set<Member> resultMembers = resultAfterVanishedInstance.getMembers();
        assertEquals(1, resultMembers.size());
        assertLocalMember(resultMembers.iterator().next(), REGISTERED);
    }

    private void assertLocalMember(Member resultMember, Boolean registered) {
        String expectedMemberName = SERVICE_INSTANCE_ID;
        URI expectedEndpoint = SERVICE_INSTANCE_URI;

        assertEquals(resultMember.getClass(), ConsistentHash.ConsistentHashMember.class);
        ConsistentHash.ConsistentHashMember result = (ConsistentHash.ConsistentHashMember) resultMember;
        if (!registered) {
            assertTrue(result.name().contains(expectedMemberName));
        } else {
            assertEquals(expectedMemberName + "[" + expectedEndpoint + "]", result.name());
        }
        assertEquals(LOAD_FACTOR, result.segmentCount());

        Optional<URI> connectionEndpointOptional = result.getConnectionEndpoint(URI.class);
        if (!registered) {
            assertFalse(connectionEndpointOptional.isPresent());
        } else {
            assertTrue(connectionEndpointOptional.isPresent());
            URI resultEndpoint = connectionEndpointOptional.orElseThrow(IllegalStateException::new);
            assertEquals(resultEndpoint, expectedEndpoint);
        }
    }

    @Test
    void testUpdateMembershipsOnHeartbeatEventFiltersInstancesWithoutCommandRouterSpecificMetadata() {
        Predicate<ServiceInstance> serviceInstanceFilter =
                serviceInstance -> !serviceInstance.getServiceId().equals("filter");
        SpringCloudCommandRouter filteringTestSubject =
                SpringCloudCommandRouter.builder()
                                        .discoveryClient(discoveryClient)
                                        .localServiceInstance(localServiceInstance)
                                        .routingStrategy(routingStrategy)
                                        .capabilityDiscoveryMode(capabilityDiscoveryMode)
                                        .serializer(serializer)
                                        .consistentHashChangeListener(consistentHashChangeListener)
                                        .serviceInstanceFilter(serviceInstanceFilter)
                                        .build();

        int expectedMemberSetSize = 1;
        String expectedServiceInstanceId = "filter";
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);

        ServiceInstance filteredServiceInstance = mock(ServiceInstance.class);
        when(filteredServiceInstance.getServiceId()).thenReturn(expectedServiceInstanceId);

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(discoveryClient.getServices())
                .thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, expectedServiceInstanceId));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(singletonList(localServiceInstance));
        when(discoveryClient.getInstances(expectedServiceInstanceId))
                .thenReturn(singletonList(filteredServiceInstance));
        when(capabilityDiscoveryMode.capabilities(localServiceInstance))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));

        // Start up command router
        filteringTestSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        // When
        filteringTestSubject.updateMemberships(mock(HeartbeatEvent.class));

        // Then
        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(discoveryClient).getInstances(expectedServiceInstanceId);
        verify(capabilityDiscoveryMode).capabilities(localServiceInstance);
        verify(capabilityDiscoveryMode, never()).capabilities(filteredServiceInstance);
        verify(consistentHashChangeListener, times(2)).onConsistentHashChanged(consistentHashCaptor.capture());

        ConsistentHash resultConsistentHash = consistentHashCaptor.getAllValues().get(1);
        Set<Member> resultMembers = resultConsistentHash.getMembers();
        assertEquals(expectedMemberSetSize, resultMembers.size());
    }

    @Test
    void testUpdateMembershipsOnHeartbeatEventTwoInstancesOnSameServiceIdUpdatesConsistentHash() {
        int expectedMemberSetSize = 2;
        ArgumentCaptor<ConsistentHash> consistentHashCaptor = ArgumentCaptor.forClass(ConsistentHash.class);

        ServiceInstance remoteInstanceWithIdenticalServiceId = mock(ServiceInstance.class);
        when(remoteInstanceWithIdenticalServiceId.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstanceWithIdenticalServiceId.getUri()).thenReturn(URI.create("remote"));

        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(discoveryClient.getServices()).thenReturn(singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(ImmutableList.of(localServiceInstance, remoteInstanceWithIdenticalServiceId));
        when(capabilityDiscoveryMode.capabilities(localServiceInstance))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));
        when(capabilityDiscoveryMode.capabilities(remoteInstanceWithIdenticalServiceId))
                .thenReturn(Optional.of(DEFAULT_MEMBER_CAPABILITIES));

        // Start up command router
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_MESSAGE_FILTER);
        // Set command router has passed the start up phase through InstanceRegisteredEvent
        testSubject.resetLocalMembership(mock(InstanceRegisteredEvent.class));

        // When
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        // Then, invoked twice because of SpringCloudCommandRouter#resetLocalMembership set-up invocation
        verify(discoveryClient, times(2)).getServices();
        verify(discoveryClient, times(2)).getInstances(SERVICE_INSTANCE_ID);
        verify(capabilityDiscoveryMode, times(2)).capabilities(localServiceInstance);
        verify(capabilityDiscoveryMode, times(2)).capabilities(remoteInstanceWithIdenticalServiceId);
        verify(consistentHashChangeListener, times(3)).onConsistentHashChanged(consistentHashCaptor.capture());

        ConsistentHash resultConsistentHash = consistentHashCaptor.getAllValues().get(2);
        Set<Member> resultMembers = resultConsistentHash.getMembers();
        assertEquals(expectedMemberSetSize, resultMembers.size());
    }

    @Test
    void testConsistentHashCreatedOnDistinctHostsShouldBeEqual() throws NoSuchFieldException {
        String atomicConsistentHashFieldName = "atomicConsistentHash";
        Field atomicConsistentHashField =
                SpringCloudCommandRouter.class.getDeclaredField(atomicConsistentHashFieldName);

        when(discoveryClient.getServices()).thenReturn(singletonList(SERVICE_INSTANCE_ID));

        int numberOfInstances = 6;
        List<ServiceInstance> serviceInstances = mockServiceInstances(numberOfInstances);
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID)).thenReturn(serviceInstances);

        List<SpringCloudCommandRouter> routers = createRoutersFor(serviceInstances);
        initAll(routers);

        List<ConsistentHash> hashes = getHashesFor(routers, atomicConsistentHashField);

        assertEquals(hashes.size(), numberOfInstances);
        hashes.forEach(hash -> assertEquals(hash, hashes.get(0)));
    }

    private List<ServiceInstance> mockServiceInstances(int number) {
        return IntStream.rangeClosed(1, number)
                        .mapToObj(i -> {
                            ServiceInstance instance = mock(ServiceInstance.class);
                            when(instance.getHost()).thenReturn("host" + i);
                            return instance;
                        })
                        .collect(toList());
    }

    private List<SpringCloudCommandRouter> createRoutersFor(List<ServiceInstance> serviceInstances) {
        return serviceInstances.stream()
                               .map(ServiceInstance::getHost)
                               .map(this::createRouterFor)
                               .collect(toList());
    }

    private SpringCloudCommandRouter createRouterFor(String host) {
        Registration localServiceInstance = mock(Registration.class);
        return SpringCloudCommandRouter.builder()
                                       .discoveryClient(discoveryClient)
                                       .localServiceInstance(localServiceInstance)
                                       .routingStrategy(routingStrategy)
                                       .capabilityDiscoveryMode(capabilityDiscoveryMode)
                                       .serializer(serializer)
                                       .consistentHashChangeListener(consistentHashChangeListener)
                                       .build();
    }

    private void initAll(List<SpringCloudCommandRouter> routers) {
        routers.forEach(router -> {
            router.updateMemberships(mock(HeartbeatEvent.class));
            router.resetLocalMembership(mock(InstanceRegisteredEvent.class));
        });
    }

    private List<ConsistentHash> getHashesFor(List<SpringCloudCommandRouter> routers, Field atomicConsistentHashField) {
        return routers.stream()
                      .map(router -> getHash(router, atomicConsistentHashField))
                      .map(AtomicReference::get)
                      .collect(toList());
    }

    private AtomicReference<ConsistentHash> getHash(SpringCloudCommandRouter router, Field atomicConsistentHashField) {
        return getFieldValue(atomicConsistentHashField, router);
    }

    @Test
    void testBuildMemberWithContextRootPropertyNameCreatesAnUriWithContextRoot() {
        SpringCloudCommandRouter customContextRootTestSubject =
                SpringCloudCommandRouter.builder()
                                        .discoveryClient(discoveryClient)
                                        .localServiceInstance(localServiceInstance)
                                        .routingStrategy(routingStrategy)
                                        .capabilityDiscoveryMode(capabilityDiscoveryMode)
                                        .serializer(serializer)
                                        .consistentHashChangeListener(consistentHashChangeListener)
                                        .contextRootMetadataPropertyName(CONTEXT_ROOT_KEY)
                                        .build();

        Map<String, String> serviceInstanceMetadata = Collections.singletonMap(CONTEXT_ROOT_KEY, "/contextRootPath");

        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(URI.create("remote"));
        when(remoteInstance.getMetadata()).thenReturn(serviceInstanceMetadata);

        Member memberWithContextRootUri = customContextRootTestSubject.buildMember(remoteInstance);

        Optional<URI> connectionEndpoint = memberWithContextRootUri.getConnectionEndpoint(URI.class);
        assertTrue(connectionEndpoint.isPresent());
        assertEquals(connectionEndpoint.get().toString(), "remote/contextRootPath");
    }

    @Test
    void testLocalBuildMemberWithContextRootPropertyNameCreatesAnUriWithContextRoot() {
        SpringCloudCommandRouter customContextRootTestSubject =
                SpringCloudCommandRouter.builder()
                                        .discoveryClient(discoveryClient)
                                        .localServiceInstance(localServiceInstance)
                                        .routingStrategy(routingStrategy)
                                        .capabilityDiscoveryMode(capabilityDiscoveryMode)
                                        .serializer(serializer)
                                        .consistentHashChangeListener(consistentHashChangeListener)
                                        .contextRootMetadataPropertyName(CONTEXT_ROOT_KEY)
                                        .build();
        customContextRootTestSubject.resetLocalMembership(null);

        Map<String, String> serviceInstanceMetadata = Collections.singletonMap(CONTEXT_ROOT_KEY, "/contextRootPath");

        ServiceInstance localInstance = mock(ServiceInstance.class);
        when(localInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localInstance.getUri()).thenReturn(URI.create("remote"));
        when(localInstance.getMetadata()).thenReturn(serviceInstanceMetadata);

        // The localServiceInstance has the same uri
        when(localServiceInstance.getUri()).thenReturn(
                UriComponentsBuilder.fromUriString("remote/contextRootPath").build().toUri()
        );

        Member memberWithContextRootUri = customContextRootTestSubject.buildMember(localInstance);

        Optional<URI> connectionEndpoint = memberWithContextRootUri.getConnectionEndpoint(URI.class);
        assertTrue(connectionEndpoint.isPresent());
        // The endpoint for the local service should get the context root, too:
        assertEquals(connectionEndpoint.get().toString(), "remote/contextRootPath");
    }

    @Test
    void testBuildWithoutDiscoveryClientThrowsAxonConfigurationException() {
        SpringCloudCommandRouter.Builder builderTestSubject =
                SpringCloudCommandRouter.builder()
                                        .localServiceInstance(localServiceInstance)
                                        .routingStrategy(routingStrategy)
                                        .capabilityDiscoveryMode(capabilityDiscoveryMode);
        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithoutLocalServiceInstanceThrowsAxonConfigurationException() {
        SpringCloudCommandRouter.Builder builderTestSubject =
                SpringCloudCommandRouter.builder()
                                        .discoveryClient(discoveryClient)
                                        .routingStrategy(routingStrategy)
                                        .capabilityDiscoveryMode(capabilityDiscoveryMode);
        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithoutRoutingStrategyThrowsAxonConfigurationException() {
        SpringCloudCommandRouter.Builder builderTestSubject =
                SpringCloudCommandRouter.builder()
                                        .discoveryClient(discoveryClient)
                                        .localServiceInstance(localServiceInstance)
                                        .capabilityDiscoveryMode(capabilityDiscoveryMode);
        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithoutCapabilityDiscoveryModeThrowsAxonConfigurationException() {
        SpringCloudCommandRouter.Builder builderTestSubject =
                SpringCloudCommandRouter.builder()
                                        .discoveryClient(discoveryClient)
                                        .localServiceInstance(localServiceInstance)
                                        .routingStrategy(routingStrategy);
        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }
}
