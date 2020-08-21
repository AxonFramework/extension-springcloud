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

package org.axonframework.extensions.springcloud.commandhandling.capabilitydiscoverymode;

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Wrapper implementation of the {@link CapabilityDiscoveryMode}, delegating all operations to another {@code
 * CapabilityDiscoveryMode} instance. When discovering {@link #capabilities(ServiceInstance)}, the given {@link
 * ServiceInstance} might be added to the ignored instance list.
 * <p>
 * {@code ServiceInstance}s are ignored whenever a {@link ServiceInstanceClientException} is thrown, upon which the
 * {@link ServiceInstance#getServiceId()} is stored for subsequent invocation, as services with the same service
 * identifier should behave identical. On the next {@link #capabilities(ServiceInstance)} iteration, the given {@code
 * ServiceInstance} is validated to <b>not</b> be present in the set of ignored service instance identifiers.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class IgnoreListingCapabilityDiscoveryMode implements CapabilityDiscoveryMode {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final CapabilityDiscoveryMode delegate;

    private final Set<String> ignoredServiceIds = new HashSet<>();

    /**
     * Build an {@link IgnoreListingCapabilityDiscoveryMode}, wrapping the given {@code delegate}.
     *
     * @param delegate the {@link CapabilityDiscoveryMode} to enhance with ignore listing logic
     */
    public IgnoreListingCapabilityDiscoveryMode(CapabilityDiscoveryMode delegate) {
        this.delegate = delegate;
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
        cleanIgnoredInstanceSet(serviceInstance);
        if (isIgnored(serviceInstance)) {
            return Optional.empty();
        }

        try {
            return delegate.capabilities(serviceInstance);
        } catch (ServiceInstanceClientException e) {
            ignoredServiceIds.add(serviceInstance.getServiceId());
            logger.info("Added ServiceInstance [{}] under host [{}] and port [{}] to the denied list, "
                                + "since we could not retrieve the required message routing information from it.",
                        serviceInstance.getServiceId(), serviceInstance.getHost(), serviceInstance.getPort());
            return Optional.empty();
        }
    }

    private void cleanIgnoredInstanceSet(ServiceInstance serviceInstance) {
        // TODO: 19-08-20 decide how to clean up old instances
//        ignoredServiceIds.removeIf(
//                deniedService -> services.stream().noneMatch(service -> equals(service, deniedService))
//        );
    }

    private boolean isIgnored(ServiceInstance service) {
        return ignoredServiceIds.stream()
                                .anyMatch(ignoredServiceId -> Objects.equals(ignoredServiceId, service.getServiceId()));
    }
}
