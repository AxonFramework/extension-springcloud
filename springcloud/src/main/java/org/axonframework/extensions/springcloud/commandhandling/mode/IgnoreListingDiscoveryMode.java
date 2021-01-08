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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Wrapper implementation of the {@link CapabilityDiscoveryMode}, delegating all operations to another {@code
 * CapabilityDiscoveryMode} instance. When discovering {@link #capabilities(ServiceInstance)}, the given {@link
 * ServiceInstance} might be added to the ignored instance list.
 * <p>
 * {@code ServiceInstance}s are ignored whenever a {@link ServiceInstanceClientException} is thrown, upon which the
 * {@code ServiceInstance} is stored for subsequent invocation. On the next {@link #capabilities(ServiceInstance)}
 * iteration, the given {@code ServiceInstance} is validated to <b>not</b> be present in the set of ignored service
 * instance identifiers.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class IgnoreListingDiscoveryMode extends AbstractCapabilityDiscoveryMode<IgnoreListingDiscoveryMode> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final CapabilityDiscoveryMode delegate;

    private final Set<ServiceInstance> ignoredServices = new HashSet<>();

    /**
     * Instantiate a {@link Builder} to be able to create a {@link IgnoreListingDiscoveryMode}.
     * <p>
     * The delegate {@link CapabilityDiscoveryMode} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a {@link Builder} to be able to create a {@link IgnoreListingDiscoveryMode}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link IgnoreListingDiscoveryMode} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the delegate {@link CapabilityDiscoveryMode} is not {@code null} and will throw an {@link
     * AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link IgnoreListingDiscoveryMode} instance
     */
    protected IgnoreListingDiscoveryMode(Builder builder) {
        super(builder);
        this.delegate = builder.delegate;
    }

    @Override
    public void updateLocalCapabilities(ServiceInstance localInstance,
                                        int loadFactor,
                                        CommandMessageFilter commandFilter) {
        delegate.updateLocalCapabilities(localInstance, loadFactor, commandFilter);
    }

    @Override
    public Optional<MemberCapabilities> capabilities(ServiceInstance serviceInstance)
            throws ServiceInstanceClientException {
        if (shouldIgnore(serviceInstance)) {
            return Optional.empty();
        }

        try {
            return delegate.capabilities(serviceInstance);
        } catch (ServiceInstanceClientException e) {
            ignoredServices.add(serviceInstance);
            logger.info("Added ServiceInstance [{}] under host [{}] and port [{}] to the denied list, "
                                + "since we could not retrieve the required member capabilities from it.",
                        serviceInstance.getServiceId(), serviceInstance.getHost(), serviceInstance.getPort(), e);
            return Optional.empty();
        }
    }

    private boolean shouldIgnore(ServiceInstance service) {
        return ignoredServices.stream().anyMatch(ignoredService -> equals(ignoredService, service));
    }

    /**
     * Implementation of the {@link org.springframework.cloud.client.ServiceInstance} in some cases do no have an {@code
     * equals()} implementation. Thus we provide our own {@code equals()} function to match a given {@code
     * ignoredInstance} with another given {@code serviceInstance}. The match is done on the service id, host and port.
     *
     * @param serviceInstance A {@link org.springframework.cloud.client.ServiceInstance} to compare with the given
     *                        {@code ignoredInstance}
     * @param ignoredInstance A {@link org.springframework.cloud.client.ServiceInstance} to compare with the given
     *                        {@code serviceInstance}
     * @return True if both instances match on the service id, host and port, and false if they do not
     */
    @SuppressWarnings("SimplifiableIfStatement")
    private boolean equals(ServiceInstance serviceInstance, ServiceInstance ignoredInstance) {
        if (serviceInstance == ignoredInstance) {
            return true;
        }
        if (ignoredInstance == null) {
            return false;
        }
        return Objects.equals(serviceInstance.getServiceId(), ignoredInstance.getServiceId())
                && Objects.equals(serviceInstance.getHost(), ignoredInstance.getHost())
                && Objects.equals(serviceInstance.getPort(), ignoredInstance.getPort());
    }

    /**
     * Builder class to instantiate a {@link IgnoreListingDiscoveryMode}.
     * <p>
     * The delegate {@link CapabilityDiscoveryMode} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder extends AbstractCapabilityDiscoveryMode.Builder<IgnoreListingDiscoveryMode> {

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

        @Override
        public IgnoreListingDiscoveryMode build() {
            return new IgnoreListingDiscoveryMode(this);
        }

        @Override
        protected void validate() {
            assertNonNull(
                    delegate, "The delegate CapabilityDiscoveryMode is a hard requirement and should be provided"
            );
        }
    }
}
