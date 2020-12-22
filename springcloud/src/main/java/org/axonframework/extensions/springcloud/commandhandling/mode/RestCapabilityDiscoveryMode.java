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

package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link CapabilityDiscoveryMode} which uses REST protocol to discover the {@link
 * MemberCapabilities} of other {@link ServiceInstance}s. It also serves the purpose of a GET endpoint, to be able to
 * share this instance's {@code MemberCapabilities}.
 * <p>
 * By default, this implementation will add services which throw a {@link ServiceInstanceClientException} to an ignore
 * list to not be requested information from in subsequent iterations.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
@RestController
@RequestMapping("${axon.distributed.spring-cloud.fallback-url:/message-routing-information}")
public class RestCapabilityDiscoveryMode extends AbstractCapabilityDiscoveryMode<RestCapabilityDiscoveryMode> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final RestTemplate restTemplate;
    private final String messageRoutingInformationEndpoint;

    /**
     * Instantiate a {@link Builder} to be able to create a {@link RestCapabilityDiscoveryMode}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link org.axonframework.serialization.xml.XStreamSerializer} instance,
     * the {@code messageRoutingInformationEndpoint} to {@code "/message-routing-information"}, and ignore listing is
     * enabled. The {@link RestTemplate} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a {@link Builder} to be able to create a {@link RestCapabilityDiscoveryMode}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link RestCapabilityDiscoveryMode} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link RestTemplate} is not {@code null} and will throw an {@link
     * AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link RestCapabilityDiscoveryMode} instance
     */
    protected RestCapabilityDiscoveryMode(Builder builder) {
        super(builder);
        this.restTemplate = builder.restTemplate;
        this.messageRoutingInformationEndpoint = builder.messageRoutingInformationEndpoint;
    }

    @Override
    public Optional<MemberCapabilities> capabilities(ServiceInstance serviceInstance) {
        try {
            return Optional.of(requestMessageRoutingInformation(serviceInstance));
        } catch (HttpClientErrorException e) {
            throw new ServiceInstanceClientException(
                    "Failed to retrieve the member's capabilities due to a client specific exception.", e
            );
        } catch (Exception e) {
            logger.info("Failed to receive the capabilities from ServiceInstance [{}] under host [{}] and port [{}]. "
                                + "Will temporarily set this instance to deny all incoming messages.",
                        serviceInstance.getServiceId(), serviceInstance.getHost(), serviceInstance.getPort());
            logger.debug("Denying all messages due to the following exception: ", e);
            return Optional.of(DefaultMemberCapabilities.INCAPABLE_MEMBER);
        }
    }

    private MemberCapabilities requestMessageRoutingInformation(ServiceInstance serviceInstance) {
        if (isLocalServiceInstance(serviceInstance)) {
            return localCapabilities;
        }
        URI destinationUri = UriComponentsBuilder.fromUri(serviceInstance.getUri())
                                                 .path(messageRoutingInformationEndpoint)
                                                 .build().toUri();

        SerializedMemberCapabilities serializedMemberCapabilities = restTemplate.exchange(
                destinationUri, HttpMethod.GET, HttpEntity.EMPTY, SerializedMemberCapabilities.class
        ).getBody();
        //noinspection ConstantConditions
        serializedMemberCapabilities.setSerializer(serializer);
        // TODO: 21-08-20 I don't like this setter...
        return serializedMemberCapabilities;
    }

    private boolean isLocalServiceInstance(ServiceInstance serviceInstance) {
        return Objects.equals(serviceInstance, localInstance)
                || Objects.equals(serviceInstance.getUri(), localInstance.getUri());
    }

    /**
     * Get the local membership information as a {@link MemberCapabilities}, thus the {@code MemberCapabilities} of the
     * node this {@link CapabilityDiscoveryMode} is a part of. The local membership information is set and updated
     * through the {@link #updateLocalCapabilities(ServiceInstance, int, CommandMessageFilter)} method.
     * <p>
     * Can either be called directly or through a GET operation on the specified {@code
     * messageRoutingInformationEndpoint} of this node.
     *
     * @return the {@link MemberCapabilities} if the node this CommandRouter implementation is part of
     */
    @GetMapping
    public MemberCapabilities getLocalMemberCapabilities() {
        return localCapabilities;
    }

    /**
     * Builder class to instantiate a {@link RestCapabilityDiscoveryMode}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link org.axonframework.serialization.xml.XStreamSerializer} instance,
     * the {@code messageRoutingInformationEndpoint} to {@code "/message-routing-information"}, and ignore listing is
     * enabled. The {@link RestTemplate} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder extends AbstractCapabilityDiscoveryMode.Builder<RestCapabilityDiscoveryMode> {

        private RestTemplate restTemplate;
        // TODO: 21-08-20 Should we change this endpoint to a different one to not collide with the SpringCloudHttpBackupCommandRouter?
        private String messageRoutingInformationEndpoint = "/message-routing-information";

        @Override
        public Builder serializer(Serializer serializer) {
            super.serializer(serializer);
            return this;
        }

        @Override
        public Builder disableIgnoreListing() {
            super.disableIgnoreListing();
            return this;
        }

        /**
         * Sets the {@link RestTemplate} used to request remote {@link Member}s their {@link MemberCapabilities} with.
         *
         * @param restTemplate the {@link RestTemplate} used to request remote {@link Member}s their {@link
         *                     MemberCapabilities} with
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder restTemplate(RestTemplate restTemplate) {
            assertNonNull(restTemplate, "RestTemplate may not be null");
            this.restTemplate = restTemplate;
            return this;
        }

        /**
         * Sets the {@code messageRoutingInformationEndpoint} of type {@link String}, which is the endpoint where to
         * retrieve the remote {@link Member}s {@link MemberCapabilities} from. Defaults to endpoint {@code
         * "/message-routing-information"}.
         *
         * @param messageRoutingInformationEndpoint the endpoint where to retrieve the remote {@link Member}s {@link
         *                                          MemberCapabilities} from
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageRoutingInformationEndpoint(String messageRoutingInformationEndpoint) {
            assertNonEmpty(messageRoutingInformationEndpoint,
                           "The messageRoutingInformationEndpoint may not be null or empty");
            this.messageRoutingInformationEndpoint = messageRoutingInformationEndpoint;
            return this;
        }

        @Override
        protected RestCapabilityDiscoveryMode buildInstance() {
            return new RestCapabilityDiscoveryMode(this);
        }

        @Override
        protected void validate() {
            assertNonNull(restTemplate, "The RestTemplate is a hard requirement and should be provided");
        }
    }
}
