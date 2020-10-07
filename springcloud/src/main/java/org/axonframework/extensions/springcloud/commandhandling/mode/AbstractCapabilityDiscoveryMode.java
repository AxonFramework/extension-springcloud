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
import org.axonframework.extensions.springcloud.commandhandling.MessageRoutingInformation;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.springframework.cloud.client.ServiceInstance;

import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of the {@link CapabilityDiscoveryMode} providing discovery basics like the {@link Serializer}
 * for the {@link MessageRoutingInformation} and the option to enabled or disable ignore listing. Ignore listing is
 * enabled by default, which ensure the {@link AbstractCapabilityDiscoveryMode} will be wrapped by a {@link
 * IgnoreListingCapabilityDiscoveryMode}.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public abstract class AbstractCapabilityDiscoveryMode<B extends CapabilityDiscoveryMode>
        implements CapabilityDiscoveryMode {

    protected final Serializer serializer;

    protected volatile ServiceInstance localInstance;
    protected volatile MemberCapabilities localCapabilities;

    /**
     * Instantiate a {@link AbstractCapabilityDiscoveryMode} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractCapabilityDiscoveryMode} instance
     */
    protected AbstractCapabilityDiscoveryMode(Builder<B> builder) {
        builder.validate();
        serializer = builder.serializerSupplier.get();
        localCapabilities = DefaultMemberCapabilities.INCAPABLE_MEMBER;
    }

    @Override
    public void updateLocalCapabilities(ServiceInstance localInstance,
                                        int loadFactor,
                                        CommandMessageFilter commandFilter) {
        this.localInstance = localInstance;
        this.localCapabilities = new DefaultMemberCapabilities(loadFactor, commandFilter);
    }

    /**
     * Builder class to instantiate an {@link AbstractCapabilityDiscoveryMode}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link org.axonframework.serialization.xml.XStreamSerializer} instance
     * and ignore listing is enabled.
     */
    protected static abstract class Builder<B extends CapabilityDiscoveryMode> {

        private Supplier<Serializer> serializerSupplier = XStreamSerializer::defaultSerializer;
        private boolean ignoreListingEnabled = true;

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@link CommandMessageFilter}. It is strongly
         * recommended to use the {@link XStreamSerializer} for this, as the {@code CommandMessageFilter} is not set up
         * to be serialized through Jackson. Defaults to the {@link XStreamSerializer}.
         *
         * @param serializer a {@link Serializer} used to de-/serialize {@link CommandMessageFilter}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<B> serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializerSupplier = () -> serializer;
            return this;
        }

        /**
         * Disables the ignore listing feature, which moves {@link Member}s which are incapable of returning any {@link
         * MessageRoutingInformation} to a dedicated list to not be ignored for the time being.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<B> disableIgnoreListing() {
            ignoreListingEnabled = false;
            return this;
        }

        /**
         * Initializes a {@link CapabilityDiscoveryMode} as specified through this Builder. Will perform additional
         * wrapping logic, like adding ignore listing logic through the {@link IgnoreListingCapabilityDiscoveryMode}.
         *
         * @return a {@link CapabilityDiscoveryMode} as specified through this Builder
         */
        public CapabilityDiscoveryMode build() {
            CapabilityDiscoveryMode capabilityDiscoveryMode = buildInstance();
            return ignoreListingEnabled
                    ? new IgnoreListingCapabilityDiscoveryMode(capabilityDiscoveryMode)
                    : capabilityDiscoveryMode;
        }

        /**
         * Initializes a {@link CapabilityDiscoveryMode} implementation as specified through this Builder.
         *
         * @return a {@link CapabilityDiscoveryMode} implementation as specified through this Builder
         */
        protected abstract B buildInstance();

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected abstract void validate();
    }
}
