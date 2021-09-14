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

package org.axonframework.extensions.springcloud.eventhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageConverter;
import org.axonframework.messaging.SubscribableMessageSource;
import org.springframework.integration.handler.AbstractMessageProducingHandler;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Initialize an SpringStreamMessageSource instance that sends all incoming spring {@link Message} to the given
 * {@link EventBus}. It is still possible for other Event Processors to subscribe to this MessageChannelAdapter.
 *
 * @author Mehdi Chitforoosh
 * @since 4.5
 */
public class SpringStreamMessageSource extends AbstractMessageProducingHandler implements SubscribableMessageSource<EventMessage<?>> {

    private final CopyOnWriteArrayList<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();
    private final SpringMessageConverter converter;

    /**
     * Instantiate a {@link SpringStreamMessageSource} based on the fields contained in the {@link SpringStreamMessageSource.Builder}.
     * The {@link EventBus} is a <b>hard requirement</b> and thus should be provided.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     * <p>
     * Will validate that the {@link EventBus} and {@link SpringMessageConverter} are not {@code null}, and will throw an
     * {@link AxonConfigurationException} if for either of them this holds.
     *
     * @param builder the {@link SpringStreamMessageSource.Builder} used to instantiate a {@link SpringStreamMessageSource} instance
     */
    protected SpringStreamMessageSource(SpringStreamMessageSource.Builder builder) {
        builder.validate();
        if (builder.eventBus != null) {
            this.messageProcessors.addAll(singletonList(builder.eventBus::publish));
        }
        this.converter = builder.converter;
    }

    /**
     * Instantiate a Builder to be able to create a {@link SpringStreamMessageSource}.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     *
     * @return a Builder to be able to create a {@link SpringStreamMessageSource}.
     */
    public static SpringStreamMessageSource.Builder builder() {
        return new SpringStreamMessageSource.Builder();
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);
        return () -> messageProcessors.remove(messageProcessor);
    }

    /**
     * Handles the given {@link Message}. If the filter refuses the message, it is ignored.
     *
     * @param message The spring message containing the event to publish
     */
    @Override
    protected void handleMessageInternal(Message<?> message) {
        Optional<EventMessage<?>> optional = converter.readSpringMessage(message);
        optional.ifPresent(eventMessage -> {
            List<? extends EventMessage<?>> messages = singletonList(eventMessage);
            for (Consumer<List<? extends EventMessage<?>>> messageProcessor : messageProcessors) {
                messageProcessor.accept(messages);
            }
        });
    }

    /**
     * Builder class to instantiate a {@link SpringStreamMessageSource}.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     */
    public static class Builder {

        private EventBus eventBus;
        private SpringMessageConverter converter;

        /**
         * Sets the eventBus for publishing events.
         *
         * @param eventBus The messageSource to subscribe
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessageSource.Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        /**
         * Sets the converter to convert spring messages to event messages and versa .
         *
         * @param converter The converter to convert messages
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessageSource.Builder converter(SpringMessageConverter converter) {
            assertNonNull(converter, "converter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Initializes a {@link SpringStreamMessageSource} as specified through this Builder.
         *
         * @return a {@link SpringStreamMessageSource} as specified through this Builder
         */
        public SpringStreamMessageSource build() {
            return new SpringStreamMessageSource(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(converter, "The Converter is a hard requirement and should be provided");
        }
    }

}
