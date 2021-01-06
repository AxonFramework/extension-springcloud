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
import org.springframework.cloud.client.ServiceInstance;

import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link CapabilityDiscoveryMode} which defaults it's own {@link MemberCapabilities} to accept
 * <b>all</b> incoming commands. It does so by enforcing the {@link CommandMessageFilter} to {@link AcceptAll} on each
 * invocation of {@link #updateLocalCapabilities(ServiceInstance, int, CommandMessageFilter)}. This implementation thus
 * <em>does not</em> take into account the given {@link CommandMessageFilter} upon {@link
 * #updateLocalCapabilities(ServiceInstance, int, CommandMessageFilter)}.
 * <p>
 * Using this implementation would be a valid solution in a homogeneous command handling system, where every node can
 * handling everything.
 * <p>
 * Note that both the {@link #updateLocalCapabilities(ServiceInstance, int, CommandMessageFilter)} and {@link
 * #capabilities(ServiceInstance)} operations are delegated towards another {@code CapabilityDiscoveryMode} instance to
 * ensure {@code MemberCapabilities} can still be found and delegated correctly.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class AcceptAllCommandsDiscoveryMode implements CapabilityDiscoveryMode {

    private final CapabilityDiscoveryMode delegate;

    /**
     * Instantiate a {@link Builder} to be able to create a {@link AcceptAllCommandsDiscoveryMode}.
     * <p>
     * The delegate {@link CapabilityDiscoveryMode} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a {@link Builder} to be able to create a {@link AcceptAllCommandsDiscoveryMode}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link AcceptAllCommandsDiscoveryMode} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the delegate {@link CapabilityDiscoveryMode} is not {@code null} and will throw an {@link
     * AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AcceptAllCommandsDiscoveryMode} instance
     */
    protected AcceptAllCommandsDiscoveryMode(Builder builder) {
        builder.validate();
        this.delegate = builder.delegate;
    }

    @Override
    public void updateLocalCapabilities(ServiceInstance localInstance,
                                        int loadFactor,
                                        CommandMessageFilter commandFilter) {
        delegate.updateLocalCapabilities(localInstance, loadFactor, AcceptAll.INSTANCE);
    }

    @Override
    public Optional<MemberCapabilities> capabilities(ServiceInstance serviceInstance)
            throws ServiceInstanceClientException {
        return delegate.capabilities(serviceInstance);
    }

    /**
     * Builder class to instantiate a {@link AcceptAllCommandsDiscoveryMode}.
     * <p>
     * The delegate {@link CapabilityDiscoveryMode} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder {

        private CapabilityDiscoveryMode delegate;

        /**
         * Sets the delegate {@link CapabilityDiscoveryMode} used to delegate the {@link #capabilities(ServiceInstance)}
         * operation too.
         *
         * @param delegate a {@link CapabilityDiscoveryMode} used to delegate the {@link #capabilities(ServiceInstance)}
         *                 operation too
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder delegate(CapabilityDiscoveryMode delegate) {
            assertNonNull(delegate, "The delegate CapabilityDiscovery may not be null or empty");
            this.delegate = delegate;
            return this;
        }

        public AcceptAllCommandsDiscoveryMode build() {
            return new AcceptAllCommandsDiscoveryMode(this);
        }

        protected void validate() {
            assertNonNull(
                    delegate, "The delegate CapabilityDiscoveryMode is a hard requirement and should be provided"
            );
        }
    }
}
