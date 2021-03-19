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
import org.axonframework.common.AxonConfigurationException;
import org.springframework.cloud.client.ServiceInstance;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract implementation of the {@link CapabilityDiscoveryMode} maintaining the local {@link ServiceInstance} and
 * {@link MemberCapabilities}.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public abstract class AbstractCapabilityDiscoveryMode<B extends CapabilityDiscoveryMode>
        implements CapabilityDiscoveryMode {

    protected AtomicReference<ServiceInstance> localInstance;
    protected AtomicReference<MemberCapabilities> localCapabilities;

    /**
     * Instantiate a {@link AbstractCapabilityDiscoveryMode} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractCapabilityDiscoveryMode} instance
     */
    protected AbstractCapabilityDiscoveryMode(Builder<B> builder) {
        builder.validate();
        localInstance = new AtomicReference<>();
        localCapabilities = new AtomicReference<>(DefaultMemberCapabilities.INCAPABLE_MEMBER);
    }

    @Override
    public void updateLocalCapabilities(ServiceInstance localInstance,
                                        int loadFactor,
                                        CommandMessageFilter commandFilter) {
        this.localInstance.getAndUpdate(old -> localInstance);
        this.localCapabilities.getAndUpdate(old -> new DefaultMemberCapabilities(loadFactor, commandFilter));
    }

    /**
     * Builder class to instantiate an {@link AbstractCapabilityDiscoveryMode}.
     *
     * @param <B> generic defining the type of {@link CapabilityDiscoveryMode} this builder will create
     */
    protected abstract static class Builder<B extends CapabilityDiscoveryMode> {

        /**
         * Initializes a {@link CapabilityDiscoveryMode} implementation as specified through this Builder.
         *
         * @return a {@link CapabilityDiscoveryMode} implementation as specified through this Builder
         */
        public abstract B build();

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected abstract void validate();
    }
}
