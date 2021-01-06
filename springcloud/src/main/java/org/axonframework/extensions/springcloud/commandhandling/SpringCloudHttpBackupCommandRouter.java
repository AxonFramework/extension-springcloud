/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.commandhandling.distributed.ConsistentHashChangeListener;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.extensions.springcloud.commandhandling.mode.IgnoreListingDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.SerializedMemberCapabilities;
import org.axonframework.serialization.Serializer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.function.Predicate;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link SpringCloudCommandRouter} which has a backup mechanism to provide Message Routing
 * Information for other nodes and to retrieve Message Routing Information from other Axon nodes.
 * <p>
 * It is annotated with the {@link org.springframework.web.bind.annotation.RestController} and contains the {@link
 * org.springframework.web.bind.annotation.GetMapping} annotated {@link SpringCloudHttpBackupCommandRouter#getLocalMessageRoutingInformation()}
 * function to have a queryable place to retrieve this node its Message Routing Information. The default endpoint for
 * this is {@code "/message-routing-information"}. To configure this endpoint the "axon.distributed.spring-cloud.fallback-url"
 * application property should be adjusted.
 * <p>
 * For retrieving the Message Routing Information from other Axon nodes, it uses a {@link
 * org.springframework.web.client.RestTemplate} to request Message Routing Information through an override of the {@code
 * SpringCloudHttpBackupCommandRouter#getMessageRoutingInformation(ServiceInstance)} function.
 *
 * @author Steven van Beelen
 * @since 3.1
 * @deprecated in favor of using the regular {@link SpringCloudCommandRouter} with the {@link
 * org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode}.
 */
@Deprecated
@RestController
@RequestMapping("${axon.distributed.spring-cloud.fallback-url:/message-routing-information}")
public class SpringCloudHttpBackupCommandRouter extends SpringCloudCommandRouter {

    private final RestCapabilityDiscoveryMode restDiscoveryMode;

    /**
     * Instantiate a Builder to be able to create a {@link SpringCloudHttpBackupCommandRouter}.
     * <p>
     * The {@code serviceInstanceFilter} is defaulted to a {@link Predicate} which always returns {@code true}, the
     * {@link ConsistentHashChangeListener} to a no-op solution and the {@code messageRoutingInformationEndpoint} to
     * {@code "/message-routing-information"}. The {@link DiscoveryClient}, {@code localServiceInstance} of type {@link
     * Registration}, the {@link RoutingStrategy}, {@link RestTemplate} and {@code messageRoutingInformationEndpoint}
     * are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link SpringCloudHttpBackupCommandRouter}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link SpringCloudHttpBackupCommandRouter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link RestTemplate} is not {@code null} and that the {@code
     * messageRoutingInformationEndpoint} is not {@code null} and empty. This assertion will throw an {@link
     * AxonConfigurationException} if either of them asserts to {@code true}. All asserts performed by the {@link
     * SpringCloudCommandRouter.Builder} are also taken into account with identical consequences.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SpringCloudHttpBackupCommandRouter} instance
     */
    protected SpringCloudHttpBackupCommandRouter(Builder builder) {
        super(builder);
        this.restDiscoveryMode = builder.restDiscoveryMode;
    }

    /**
     * Get the local {@link MessageRoutingInformation}, thus the MessageRoutingInformation of the node this
     * CommandRouter is a part of. Can either be called directly or through a GET operation on the specified {@code
     * messageRoutingInformationEndpoint} of this node.
     *
     * @return the {@link MessageRoutingInformation} if the node this CommandRouter implementation is part of
     * @deprecated in favor of the {@link RestCapabilityDiscoveryMode#getLocalMemberCapabilities()} method
     */
    @Deprecated
    @GetMapping
    public MessageRoutingInformation getLocalMessageRoutingInformation() {
        SerializedMemberCapabilities serializedCapabilities = restDiscoveryMode.getLocalMemberCapabilities();
        return new MessageRoutingInformation(serializedCapabilities.getLoadFactor(),
                                             serializedCapabilities.getSerializedCommandFilter(),
                                             serializedCapabilities.getSerializedCommandFilterType());
    }

    /**
     * Builder class to instantiate a {@link SpringCloudHttpBackupCommandRouter}.
     * <p>
     * The {@code serviceInstanceFilter} is defaulted to a {@link Predicate} which always returns {@code true}, the
     * {@link ConsistentHashChangeListener} to a no-op solution and the {@code messageRoutingInformationEndpoint} to
     * {@code "/message-routing-information"}. The {@link DiscoveryClient}, {@code localServiceInstance} of type {@link
     * Registration}, the {@link RoutingStrategy}, {@link RestTemplate} and {@code messageRoutingInformationEndpoint}
     * are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends SpringCloudCommandRouter.Builder {

        private RestTemplate restTemplate;
        private String messageRoutingInformationEndpoint = "/message-routing-information";
        private RestCapabilityDiscoveryMode restDiscoveryMode;

        @Override
        public Builder discoveryClient(DiscoveryClient discoveryClient) {
            super.discoveryClient(discoveryClient);
            return this;
        }

        @Override
        public Builder localServiceInstance(Registration localServiceInstance) {
            super.localServiceInstance(localServiceInstance);
            return this;
        }

        @Override
        public Builder routingStrategy(RoutingStrategy routingStrategy) {
            super.routingStrategy(routingStrategy);
            return this;
        }

        @Override
        public Builder serviceInstanceFilter(
                Predicate<ServiceInstance> serviceInstanceFilter) {
            super.serviceInstanceFilter(serviceInstanceFilter);
            return this;
        }

        @Override
        public Builder consistentHashChangeListener(ConsistentHashChangeListener consistentHashChangeListener) {
            super.consistentHashChangeListener(consistentHashChangeListener);
            return this;
        }

        @Override
        public Builder contextRootMetadataPropertyName(String contextRootMetadataPropertyName) {
            super.contextRootMetadataPropertyName(contextRootMetadataPropertyName);
            return this;
        }

        @Override
        public Builder serializer(Serializer serializer) {
            super.serializer(serializer);
            return this;
        }

        /**
         * Sets the {@link RestTemplate} used as the backup mechanism to request another member's {@link
         * MessageRoutingInformation} with.
         *
         * @param restTemplate the {@link RestTemplate} used as the backup mechanism to request another member's {@link
         *                     MessageRoutingInformation} with.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder restTemplate(RestTemplate restTemplate) {
            assertNonNull(restTemplate, "RestTemplate may not be null");
            this.restTemplate = restTemplate;
            return this;
        }

        /**
         * Sets the {@code messageRoutingInformationEndpoint} of type {@link String}, which is the endpoint where to
         * retrieve the another nodes message routing information from. Defaults to endpoint {@code
         * "/message-routing-information"}.
         *
         * @param messageRoutingInformationEndpoint the endpoint where to retrieve the another nodes message routing
         *                                          information from
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageRoutingInformationEndpoint(String messageRoutingInformationEndpoint) {
            assertNonEmpty(
                    messageRoutingInformationEndpoint,
                    "The messageRoutingInformationEndpoint may not be null or empty"
            );
            this.messageRoutingInformationEndpoint = messageRoutingInformationEndpoint;
            return this;
        }

        /**
         * Enforces the back up solution provided by this {@link SpringCloudCommandRouter} to be used for retrieving
         * {@link MessageRoutingInformation}. This should be toggled on if the utilized Spring Cloud Discovery mechanism
         * has an inconsistent metadata update policy on the {@link ServiceInstance}, which can lead to inconsistent
         * {@code MessageRoutingInformation} being shared.
         *
         * @return the current Builder instance, for fluent interfacing
         * @deprecated in favor of using the {@link SpringCloudCommandRouter} in combination with the {@link
         * RestCapabilityDiscoveryMode}
         */
        @Deprecated
        public Builder enforceHttpDiscovery() {
            return this;
        }

        /**
         * Initializes a {@link SpringCloudHttpBackupCommandRouter} as specified through this Builder.
         *
         * @return a {@link SpringCloudHttpBackupCommandRouter} as specified through this Builder
         */
        public SpringCloudHttpBackupCommandRouter build() {
            return new SpringCloudHttpBackupCommandRouter(this);
        }

        @Override
        protected void validate() {
            // Enforce usage of the RestCapabilityDiscoveryMode by setting it regardless of the provided CapabilityDiscoveryMode
            restDiscoveryMode =
                    RestCapabilityDiscoveryMode.builder()
                                               .messageCapabilitiesEndpoint(messageRoutingInformationEndpoint)
                                               .restTemplate(restTemplate)
                                               .serializer(serializerSupplier.get())
                                               .build();
            capabilityDiscoveryMode(IgnoreListingDiscoveryMode.builder().delegate(restDiscoveryMode).build());

            super.validate();
            assertNonNull(restTemplate, "The RestTemplate is a hard requirement and should be provided");
            assertNonEmpty(
                    messageRoutingInformationEndpoint,
                    "The messageRoutingInformationEndpoint is a hard requirement and should be provided"
            );
        }
    }
}
