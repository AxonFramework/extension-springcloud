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

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link CapabilityDiscoveryMode} which uses a {@link RestTemplate} to discover the {@link
 * MemberCapabilities} of other {@link ServiceInstance}s.
 * <p>
 * Note that when this REST {@code CapabilityDiscoveryMode} is selected, a member's capabilities should also be
 * retrievable. To that end a {@link MemberCapabilitiesController} is <b>required</b> to be present.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class RestCapabilityDiscoveryMode extends AbstractCapabilityDiscoveryMode<RestCapabilityDiscoveryMode> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Serializer serializer;
    private final RestTemplate restTemplate;
    private final String memberCapabilitiesEndpoint;

    /**
     * Instantiate a {@link Builder} to be able to create a {@link RestCapabilityDiscoveryMode}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link org.axonframework.serialization.xml.XStreamSerializer} instance
     * and the {@code messageCapabilitiesEndpoint} to {@code "/message-routing-information"}. The {@link RestTemplate}
     * is a <b>hard requirement</b> and as such should be provided.
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
        this.serializer = builder.serializerSupplier.get();
        this.restTemplate = builder.restTemplate;
        this.memberCapabilitiesEndpoint = builder.messageCapabilitiesEndpoint;
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
            logger.debug("Service Instance [{}] is denying all messages due to the following exception: ",
                         serviceInstance, e);
            return Optional.of(DefaultMemberCapabilities.INCAPABLE_MEMBER);
        }
    }

    private MemberCapabilities requestMessageRoutingInformation(ServiceInstance serviceInstance) {
        if (isLocalServiceInstance(serviceInstance)) {
            return localCapabilities.get();
        }
        URI destinationUri = UriComponentsBuilder.fromUri(serviceInstance.getUri())
                                                 .path(memberCapabilitiesEndpoint)
                                                 .build().toUri();

        SerializedMemberCapabilities serializedMemberCapabilities = restTemplate.exchange(
                destinationUri, HttpMethod.GET, HttpEntity.EMPTY, SerializedMemberCapabilities.class
        ).getBody();
        //noinspection ConstantConditions
        return new DefaultMemberCapabilities(serializedMemberCapabilities, serializer);
    }

    private boolean isLocalServiceInstance(ServiceInstance serviceInstance) {
        return Objects.equals(serviceInstance, localInstance.get())
                || Objects.equals(serviceInstance.getUri(), localInstance.get().getUri());
    }

    /**
     * Get the local membership information as a {@link SerializedMemberCapabilities}, thus the {@link
     * MemberCapabilities} of the node this {@link CapabilityDiscoveryMode} is a part of. The local membership
     * information is set and updated through the {@link #updateLocalCapabilities(ServiceInstance, int,
     * CommandMessageFilter)} method.
     *
     * @return the {@link SerializedMemberCapabilities} of the node this {@link CapabilityDiscoveryMode} implementation
     * is part of
     */
    public SerializedMemberCapabilities getLocalMemberCapabilities() {
        return SerializedMemberCapabilities.build(localCapabilities.get(), serializer);
    }

    /**
     * Builder class to instantiate a {@link RestCapabilityDiscoveryMode}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link org.axonframework.serialization.xml.XStreamSerializer} instance
     * and the {@code messageCapabilitiesEndpoint} to {@code "/message-routing-information"}. The {@link RestTemplate}
     * is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder extends AbstractCapabilityDiscoveryMode.Builder<RestCapabilityDiscoveryMode> {

        private Supplier<Serializer> serializerSupplier = XStreamSerializer::defaultSerializer;
        private RestTemplate restTemplate;
        private String messageCapabilitiesEndpoint = "/member-capabilities";

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@link CommandMessageFilter}. Defaults to the {@link
         * XStreamSerializer}.
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
         * Sets the {@code messageCapabilitiesEndpoint} of type {@link String}, which is the endpoint where to retrieve
         * the remote {@link Member}s {@link MemberCapabilities} from. Defaults to endpoint {@code
         * "/message-routing-information"}.
         *
         * @param messageCapabilitiesEndpoint the endpoint where to retrieve the remote {@link Member}s {@link
         *                                    MemberCapabilities} from
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageCapabilitiesEndpoint(String messageCapabilitiesEndpoint) {
            assertNonEmpty(messageCapabilitiesEndpoint, "The messageCapabilitiesEndpoint may not be null or empty");
            this.messageCapabilitiesEndpoint = messageCapabilitiesEndpoint;
            return this;
        }

        @Override
        public RestCapabilityDiscoveryMode build() {
            return new RestCapabilityDiscoveryMode(this);
        }

        @Override
        protected void validate() {
            assertNonNull(restTemplate, "The RestTemplate is a hard requirement and should be provided");
        }
    }
}
