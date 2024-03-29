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

package org.axonframework.extensions.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.ConsistentHashChangeListener;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.event.EventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link CommandRouter} implementation which uses Spring Cloud's {@link DiscoveryClient}s to propagate its Command
 * Message Routing Information, and to discover other Axon nodes and retrieve their Message Routing Information. It does
 * so by utilizing the metadata contained in a {@link ServiceInstance} for storing the Message Routing Information in.
 * Other nodes discovered through the DiscoveryClient system which do not contain any of the required Message Routing
 * Information fields will be black listed, so not to perform any unneeded additional checks on that node.
 * <p>
 * The {@code localServiceInstance} is added for correct deviation between the local instance and the instances
 * retrieved from the DiscoveryClient. A {@link RoutingStrategy} is in place to decide which instance will handle a
 * given Command Message. Lastly, a {@link Predicate} of generic type {@link ServiceInstance} can be provided to filter
 * out specific service instances which need to be disregarded, and a {@link ConsistentHashChangeListener} is
 * configurable to notify if the memberships have been updated.
 *
 * @author Steven van Beelen
 * @since 3.0
 */
public class SpringCloudCommandRouter implements CommandRouter {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String LOAD_FACTOR = "loadFactor";
    private static final String SERIALIZED_COMMAND_FILTER = "serializedCommandFilter";
    private static final String SERIALIZED_COMMAND_FILTER_CLASS_NAME = "serializedCommandFilterClassName";

    private final DiscoveryClient discoveryClient;
    private final Registration localServiceInstance;
    private final RoutingStrategy routingStrategy;
    private final CapabilityDiscoveryMode capabilityDiscoveryMode;
    protected final Serializer serializer;
    private final Predicate<ServiceInstance> serviceInstanceFilter;
    private final ConsistentHashChangeListener consistentHashChangeListener;
    private final String contextRootMetadataPropertyName;

    private final AtomicReference<ConsistentHash> atomicConsistentHash = new AtomicReference<>(new ConsistentHash());

    private volatile boolean registered = false;

    /**
     * Instantiate a Builder to be able to create a {@link SpringCloudCommandRouter}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link XStreamSerializer}, the {@code serviceInstanceFilter} to accept
     * all instances, and the {@link ConsistentHashChangeListener} to a no-op solution. The {@link DiscoveryClient},
     * {@code localServiceInstance} of type {@link Registration}, the {@link RoutingStrategy} and the {@link
     * CapabilityDiscoveryMode} are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link SpringCloudCommandRouter}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link SpringCloudCommandRouter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link DiscoveryClient}, {@code localServiceInstance} of type {@link Registration}, {@link
     * RoutingStrategy} and {@link CapabilityDiscoveryMode} are not {@code null}, and will throw an {@link
     * AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SpringCloudCommandRouter} instance
     */
    protected SpringCloudCommandRouter(Builder builder) {
        builder.validate();
        discoveryClient = builder.discoveryClient;
        localServiceInstance = builder.localServiceInstance;
        routingStrategy = builder.routingStrategy;
        capabilityDiscoveryMode = builder.capabilityDiscoveryMode;
        serializer = builder.serializerSupplier.get();
        serviceInstanceFilter = builder.serviceInstanceFilter;
        consistentHashChangeListener = builder.consistentHashChangeListener;
        contextRootMetadataPropertyName = builder.contextRootMetadataPropertyName;
    }

    /**
     * Boolean check if the metadata {@link java.util.Map} of the given {@link org.springframework.cloud.client.ServiceInstance}
     * contains any of the expected message routing info keys.
     *
     * @param serviceInstance The {@link org.springframework.cloud.client.ServiceInstance} to check its metadata keys
     *                        from
     * @return true if the given {@code serviceInstance} contains all expected message routing info keys; false if one
     * of the expected message routing info keys is missing.
     * @deprecated no current {@link CapabilityDiscoveryMode} bases itself on {@link ServiceInstance#getMetadata()} and
     * as such this filter will be removed
     */
    @Deprecated
    public static boolean serviceInstanceMetadataContainsMessageRoutingInformation(ServiceInstance serviceInstance) {
        Map<String, String> serviceInstanceMetadata = serviceInstance.getMetadata();
        return serviceInstanceMetadata != null &&
                serviceInstanceMetadata.containsKey(LOAD_FACTOR) &&
                serviceInstanceMetadata.containsKey(SERIALIZED_COMMAND_FILTER) &&
                serviceInstanceMetadata.containsKey(SERIALIZED_COMMAND_FILTER_CLASS_NAME);
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> commandMessage) {
        return atomicConsistentHash.get().getMember(routingStrategy.getRoutingKey(commandMessage), commandMessage);
    }

    @Override
    public void updateMembership(int loadFactor, CommandMessageFilter commandFilter) {
        capabilityDiscoveryMode.updateLocalCapabilities(localServiceInstance, loadFactor, commandFilter);
        consistentHashChangeListener.onConsistentHashChanged(atomicConsistentHash.updateAndGet(
                consistentHash -> consistentHash.with(buildMember(localServiceInstance), loadFactor, commandFilter)
        ));
    }

    /**
     * Update the local member and all the other remote members known by the {@link
     * org.springframework.cloud.client.discovery.DiscoveryClient} to be able to have an as up-to-date awareness of
     * which actions which members can handle. This function is automatically triggered by an (unused) {@link
     * org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent}. Upon this event we may assume that the
     * application has fully start up. Because of this we can update the local member with the correct name and {@link
     * java.net.URI}, as initially these were not provided by the {@link org.springframework.cloud.client.serviceregistry.Registration}
     * yet.
     *
     * @param event an unused {@link org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent}, serves
     *              as a trigger for this function
     * @see SpringCloudCommandRouter#buildMember(ServiceInstance)
     */
    @SuppressWarnings("unused")
    @EventListener
    public void resetLocalMembership(InstanceRegisteredEvent<?> event) {
        registered = true;

        Optional<Member> startUpPhaseLocalMember =
                atomicConsistentHash.get().getMembers().stream()
                                    .filter(Member::local)
                                    .findFirst();

        updateMemberships();

        startUpPhaseLocalMember.ifPresent(localMember -> {
            if (logger.isDebugEnabled()) {
                logger.debug("Resetting local membership for [{}].", localMember);
            }
            atomicConsistentHash.updateAndGet(consistentHash -> consistentHash.without(localMember));
        });
    }

    /**
     * Update the memberships of all nodes known by the {@link DiscoveryClient} to be able to have an as up-to-date
     * awareness of which actions which members can handle. This function is automatically triggered by an (unused)
     * {@link HeartbeatEvent}. The interval of this {@code HeartbeatEvent} thus specifies the speed within which cluster
     * topologies, a.k.a. each nodes command handling capabilities, are propagated.
     *
     * @param event an unused {@link HeartbeatEvent}, serves as a trigger for this function
     */
    @EventListener
    @SuppressWarnings("UnusedParameters")
    public void updateMemberships(HeartbeatEvent event) {
        updateMemberships();
    }

    private void updateMemberships() {
        AtomicReference<ConsistentHash> updatedConsistentHash = new AtomicReference<>(new ConsistentHash());

        List<ServiceInstance> instances = discoveryClient.getServices().stream()
                                                         .map(discoveryClient::getInstances)
                                                         .flatMap(Collection::stream)
                                                         .filter(serviceInstanceFilter)
                                                         .collect(Collectors.toList());

        // If no instances are discovered, at least make sure the local instance is updated
        if (instances.isEmpty()) {
            instances.add(localServiceInstance);
        }

        for (ServiceInstance serviceInstance : instances) {
            logger.debug("Updating membership for service instance: [{}]", serviceInstance);
            capabilityDiscoveryMode.capabilities(serviceInstance)
                                   .ifPresent(memberCapabilities -> updatedConsistentHash.updateAndGet(
                                           consistentHash -> consistentHash.with(
                                                   buildMember(serviceInstance),
                                                   memberCapabilities.getLoadFactor(),
                                                   memberCapabilities.getCommandFilter()
                                           )
                                   ));
        }

        ConsistentHash newConsistentHash = updatedConsistentHash.get();
        atomicConsistentHash.set(newConsistentHash);
        consistentHashChangeListener.onConsistentHashChanged(newConsistentHash);
    }

    /**
     * Instantiate a {@link Member} of type {@link java.net.URI} based on the provided {@code serviceInstance}. This
     * {@code Member} is later used to send, for example, {@link CommandMessage}s to.
     * <p>
     * A deviation is made between a local and a remote member, since if a local node is selected to handle the command,
     * the local command bus may be leveraged. The check if a {@link ServiceInstance} is local is based on two potential
     * situations:
     * <ol>
     *     <li>The given {@code serviceInstance} is identical to the {@code localServiceInstance} thus making it local.</li>
     *     <li>The URI of the given {@code serviceInstance} is identical to the URI of the {@code localServiceInstance}.</li>
     * </ol>
     * <p>
     * We take this route because we've identified that several Spring Cloud implementations do not contain any URI
     * information during the start up phase and as a side effect will throw exception if the URI is requested from it.
     * We thus return a simplified {@link Member} for the {@code localServiceInstance} to not trigger this exception.
     *
     * @param serviceInstance a {@link ServiceInstance} to build a {@link Member} for
     * @return a {@link Member} based on the contents of the provided {@code serviceInstance}
     */
    protected Member buildMember(ServiceInstance serviceInstance) {
        return isLocalServiceInstance(serviceInstance)
                ? buildLocalMember(serviceInstance)
                : buildRemoteMember(serviceInstance);
    }

    private boolean isLocalServiceInstance(ServiceInstance serviceInstance) {
        return serviceInstance.equals(localServiceInstance)
                || Objects.equals(serviceInstance.getUri(), localServiceInstance.getUri());
    }

    private Member buildLocalMember(ServiceInstance localServiceInstance) {
        String localServiceId = localServiceInstance.getServiceId();
        URI emptyEndpoint = null;
        //noinspection ConstantConditions | added null variable for clarity
        return registered
                ? new SimpleMember<>(buildName(localServiceId, buildRemoteUriWithContextRoot(localServiceInstance)),
                                     localServiceInstance.getUri(),
                                     SimpleMember.LOCAL_MEMBER,
                                     this::suspect)
                : new SimpleMember<>(localServiceId.toUpperCase() + "[LOCAL]",
                                     emptyEndpoint,
                                     SimpleMember.LOCAL_MEMBER,
                                     this::suspect);
    }

    private Member buildRemoteMember(ServiceInstance remoteServiceInstance) {
        URI serviceWithContextRootUri = buildRemoteUriWithContextRoot(remoteServiceInstance);

        return new SimpleMember<>(buildName(remoteServiceInstance.getServiceId(), serviceWithContextRootUri),
                                  serviceWithContextRootUri,
                                  SimpleMember.REMOTE_MEMBER,
                                  this::suspect);
    }

    private String buildName(String serviceId, URI serviceUri) {
        return serviceId.toUpperCase() + "[" + serviceUri + "]";
    }

    private URI buildRemoteUriWithContextRoot(ServiceInstance serviceInstance) {
        if (contextRootMetadataPropertyName == null) {
            return serviceInstance.getUri();
        }

        if (serviceInstance.getMetadata() == null) {
            logger.warn("A contextRootMetadataPropertyName [{}] has been provided, but the metadata is null. " +
                                "Defaulting to '/' as the context root.", contextRootMetadataPropertyName);
            return serviceInstance.getUri();
        }

        if (!serviceInstance.getMetadata().containsKey(contextRootMetadataPropertyName)) {
            logger.info("The service instance metadata does not contain a property with name '{}'. " +
                                "Defaulting to '/' as the context root.", contextRootMetadataPropertyName);
            return serviceInstance.getUri();
        }

        return UriComponentsBuilder.fromUri(serviceInstance.getUri())
                                   .path(serviceInstance.getMetadata().get(contextRootMetadataPropertyName))
                                   .build()
                                   .toUri();
    }

    private ConsistentHash suspect(Member member) {
        ConsistentHash newConsistentHash =
                atomicConsistentHash.updateAndGet(consistentHash -> consistentHash.without(member));
        consistentHashChangeListener.onConsistentHashChanged(newConsistentHash);
        return newConsistentHash;
    }

    /**
     * Builder class to instantiate a {@link SpringCloudCommandRouter}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link XStreamSerializer}, the {@code serviceInstanceFilter} to accept
     * all instances, and the {@link ConsistentHashChangeListener} to a no-op solution. The {@link DiscoveryClient},
     * {@code localServiceInstance} of type {@link Registration}, the {@link RoutingStrategy} and the {@link
     * CapabilityDiscoveryMode} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private DiscoveryClient discoveryClient;
        private Registration localServiceInstance;
        private RoutingStrategy routingStrategy;
        private CapabilityDiscoveryMode capabilityDiscoveryMode;
        protected Supplier<Serializer> serializerSupplier = XStreamSerializer::defaultSerializer;
        private Predicate<ServiceInstance> serviceInstanceFilter = serviceInstance -> true;
        private ConsistentHashChangeListener consistentHashChangeListener = ConsistentHashChangeListener.noOp();
        private String contextRootMetadataPropertyName;

        /**
         * Sets the {@link DiscoveryClient} used to discovery and notify other nodes. Used to update its own membership
         * as a {@code CommandRouter} and to create its awareness of available nodes to send commands to.
         *
         * @param discoveryClient the {@link DiscoveryClient} used to discovery and notify other nodes
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder discoveryClient(DiscoveryClient discoveryClient) {
            assertNonNull(discoveryClient, "DiscoveryClient may not be null");
            this.discoveryClient = discoveryClient;
            return this;
        }

        /**
         * Sets the {@link Registration}, representing the local Service Instance of this application. Necessary to
         * differentiate other instances from the local instance to ensure correct message routing.
         *
         * @param localServiceInstance the {@link Registration}, representing the local Service Instance of this
         *                             application
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder localServiceInstance(Registration localServiceInstance) {
            assertNonNull(localServiceInstance, "Registration may not be null");
            this.localServiceInstance = localServiceInstance;
            return this;
        }

        /**
         * Sets the {@link RoutingStrategy} used to define the key on which Command Messages are routed to their
         * respective nodes.
         *
         * @param routingStrategy the {@link RoutingStrategy} used to define the key on which Command Messages are
         *                        routed to their respective nodes
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder routingStrategy(RoutingStrategy routingStrategy) {
            assertNonNull(routingStrategy, "RoutingStrategy may not be null");
            this.routingStrategy = routingStrategy;
            return this;
        }

        /**
         * Defines the {@link CapabilityDiscoveryMode} used by this {@link CommandRouter} implementation. The {@code
         * CapabilityDiscoveryMode} is in charge of discovering the capabilities of other nodes and sharing this node's
         * capabilities.
         *
         * @param capabilityDiscoveryMode a {@link CapabilityDiscoveryMode} in charge of discovering the capabilities of
         *                                other nodes and sharing this node's capabilities
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder capabilityDiscoveryMode(CapabilityDiscoveryMode capabilityDiscoveryMode) {
            assertNonNull(capabilityDiscoveryMode, "CapabilityDiscoveryMode may not be null");
            this.capabilityDiscoveryMode = capabilityDiscoveryMode;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@link CommandMessageFilter}. It is strongly
         * recommended to use the {@link XStreamSerializer} for this, as the {@code CommandMessageFilter} is not set up
         * to be serialized through Jackson. Defaults to the {@link XStreamSerializer}.
         *
         * @param serializer a {@link Serializer} used to de-/serialize {@link CommandMessageFilter}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializerSupplier = () -> serializer;
            return this;
        }

        /**
         * Sets a {@link Predicate} of generic type {@link ServiceInstance}, used to filter out {@code ServiceInstance}s
         * from the membership update loop. Defaults to allow all {@code ServiceInstance}s.
         *
         * @param serviceInstanceFilter the {@link Predicate} of generic type {@link ServiceInstance}, used to filter
         *                              out ServiceInstances from the membership update loop
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serviceInstanceFilter(Predicate<ServiceInstance> serviceInstanceFilter) {
            assertNonNull(serviceInstanceFilter, "ServiceInstanceFilter may not be null");
            this.serviceInstanceFilter = serviceInstanceFilter;
            return this;
        }

        /**
         * Sets the {@link ConsistentHashChangeListener} which is notified when a change in membership has
         * <em>potentially</em> caused a change in the consistent hash. Defaults to a no-op solution.
         *
         * @param consistentHashChangeListener the {@link ConsistentHashChangeListener} which is notified when a change
         *                                     in membership has <em>potentially</em> caused a change in the consistent
         *                                     hash
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder consistentHashChangeListener(ConsistentHashChangeListener consistentHashChangeListener) {
            assertNonNull(consistentHashChangeListener, "ConsistentHashChangeListener may not be null");
            this.consistentHashChangeListener = consistentHashChangeListener;
            return this;
        }

        /**
         * Sets a property key to be expected in the {@link ServiceInstance#getMetadata()} returned object, defining the
         * context root for the given {@link ServiceInstance}. Will be used to correctly configure the URI of a {@link
         * Member}. Will default to {@code null}.
         *
         * @param contextRootMetadataPropertyName the optional metadata property field of a {@link ServiceInstance} that
         *                                        contains the context root path of the service in question
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder contextRootMetadataPropertyName(String contextRootMetadataPropertyName) {
            this.contextRootMetadataPropertyName = contextRootMetadataPropertyName;
            return this;
        }

        /**
         * Initializes a {@link SpringCloudCommandRouter} as specified through this Builder.
         *
         * @return a {@link SpringCloudCommandRouter} as specified through this Builder
         */
        public SpringCloudCommandRouter build() {
            return new SpringCloudCommandRouter(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(discoveryClient, "The DiscoveryClient is a hard requirement and should be provided");
            assertNonNull(localServiceInstance, "The Registration is a hard requirement and should be provided");
            assertNonNull(routingStrategy, "The RoutingStrategy is a hard requirement and should be provided");
            assertNonNull(
                    capabilityDiscoveryMode, "The CapabilityDiscoveryMode is a hard requirement and should be provided"
            );
        }
    }
}
