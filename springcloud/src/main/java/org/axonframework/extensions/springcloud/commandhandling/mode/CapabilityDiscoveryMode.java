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
import org.springframework.cloud.client.ServiceInstance;

import java.util.Optional;

/**
 * Interface towards defining an approach of discovering and maintaining the local and remote {@link MemberCapabilities}
 * of {@link ServiceInstance}s.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public interface CapabilityDiscoveryMode {

    /**
     * Update the capabilities of the {@code localInstance}, defined through the given {@code loadFactor} and {@code
     * commandFilter}.
     *
     * @param localInstance the local {@link ServiceInstance}
     * @param loadFactor    the load factor of the local instance, defining the amount of load this instance can carry
     * @param commandFilter a filter defining the {@link org.axonframework.commandhandling.CommandMessage}s the local
     *                      instance is capable of handling
     */
    void updateLocalCapabilities(ServiceInstance localInstance, int loadFactor, CommandMessageFilter commandFilter);

    /**
     * Discovers the capabilities of the given {@code serviceInstance}, returning an {@link Optional} {@link
     * MemberCapabilities}.
     *
     * @param serviceInstance the {@link ServiceInstance} to discover {@link MemberCapabilities} for
     * @return an {@link Optional} {@link MemberCapabilities}, based on the given {@code serviceInstance}
     * @throws ServiceInstanceClientException whenever the {@link ServiceInstance} returns a client specific exception
     */
    Optional<MemberCapabilities> capabilities(ServiceInstance serviceInstance) throws ServiceInstanceClientException;
}
