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
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.serialization.Serializer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.client.RestTemplate;

/**
 * Simple implementation of the {@link CapabilityDiscoveryMode}, which will discover {@link
 * org.springframework.cloud.client.ServiceInstance}s and assume they can handle the exact same set of commands as the
 * local instance. This {@code DiscoveryMode} us thus a valid solution in a homogeneous command handling system. As
 * such, this implementation
 * <em>does not</em> take into account the given {@link CommandMessageFilter} upon {@link
 * #updateLocalCapabilities(ServiceInstance, int, CommandMessageFilter)} for example. Instead, it will set the command
 * filter to {@link AcceptAll}. Additionally, it does not employ any notion of deny listing discovered {@link
 * org.springframework.cloud.client.ServiceInstance}s.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class SimpleCapabilityDiscoveryMode extends RestCapabilityDiscoveryMode {

    /**
     * Instantiate a {@link Builder} to be able to create a {@link SimpleCapabilityDiscoveryMode}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link org.axonframework.serialization.xml.XStreamSerializer} instance
     * and the {@code messageRoutingInformationEndpoint} to {@code "/message-routing-information"}. The {@link
     * RestTemplate} is a <b>hard requirement</b> and as such should be provided.
     * <p>
     * Note that {@link Builder#disableIgnoreListing()} is invoked by default.
     *
     * @return a {@link Builder} to be able to create a {@link SimpleCapabilityDiscoveryMode}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link SimpleCapabilityDiscoveryMode} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link RestTemplate} is not {@code null} and will throw an {@link
     * AxonConfigurationException} if this is the case.
     * <p>
     * Note that {@link Builder#disableIgnoreListing()} is invoked by default.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleCapabilityDiscoveryMode} instance
     */
    protected SimpleCapabilityDiscoveryMode(Builder builder) {
        super(builder);
    }

    @Override
    public void updateLocalCapabilities(ServiceInstance localInstance,
                                        int loadFactor,
                                        CommandMessageFilter commandFilter) {
        super.updateLocalCapabilities(localInstance, loadFactor, AcceptAll.INSTANCE);
    }

    /**
     * Builder class to instantiate a {@link SimpleCapabilityDiscoveryMode}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link org.axonframework.serialization.xml.XStreamSerializer} instance,
     * the {@code messageRoutingInformationEndpoint} to {@code "/message-routing-information"}, and ignore listing is
     * enabled. The {@link RestTemplate} is a <b>hard requirement</b> and as such should be provided.
     * <p>
     * Note that {@link Builder#disableIgnoreListing()} is invoked by default.
     */
    public static class Builder extends RestCapabilityDiscoveryMode.Builder {

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

        @Override
        public Builder restTemplate(RestTemplate restTemplate) {
            super.restTemplate(restTemplate);
            return this;
        }

        @Override
        public Builder messageRoutingInformationEndpoint(String messageRoutingInformationEndpoint) {
            super.messageRoutingInformationEndpoint(messageRoutingInformationEndpoint);
            return this;
        }

        @Override
        protected SimpleCapabilityDiscoveryMode buildInstance() {
            disableIgnoreListing();
            return new SimpleCapabilityDiscoveryMode(this);
        }
    }
}
