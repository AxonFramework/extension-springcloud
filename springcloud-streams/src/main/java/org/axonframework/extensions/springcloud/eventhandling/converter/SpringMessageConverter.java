/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.springcloud.eventhandling.converter;

import org.axonframework.eventhandling.EventMessage;
import org.springframework.messaging.Message;

import java.util.Optional;

/**
 * Interface describing a mechanism that converts Spring Messages from an Axon Messages and vice versa.
 *
 * @author Mehdi Chitforoosh
 * @since 4.5
 */
public interface SpringMessageConverter {

    /**
     * Creates an Spring Message from given {@code eventMessage}.
     *
     * @param eventMessage The EventMessage to create the Spring Message from
     * @return an Spring Message containing the payload and headers
     * Broker.
     */
    Message<?> createSpringMessage(EventMessage<?> eventMessage);

    /**
     * Reconstruct an EventMessage from the given spring message. The returned value
     * resolves to a message.
     *
     * @param message spring message
     * @return The Event Message to publish on the local event processors
     */
    Optional<EventMessage<?>> readSpringMessage(Message<?> message);
}
