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
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Collections.singletonList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Initialize the adapter to publish all incoming events to the subscribed processors. Note that this instance should
 * be registered as a consumer of a Spring Message Channel.
 *
 * @author Mehdi chitforoosh
 * @since 4.5
 */
public class SpringStreamMessageProcessor extends AbstractMessageProducingHandler implements SubscribableMessageSource<EventMessage<?>> {

    private final SubscribableMessageSource<EventMessage<?>> messageSource;
    private final CopyOnWriteArrayList<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();
    private Predicate<? super EventMessage<?>> filter;
    private final SpringMessageConverter converter;

    /**
     * Instantiate a {@link SpringStreamMessageProcessor} based on the fields contained in the {@link SpringStreamMessageProcessor.Builder}.
     * The {@link SubscribableMessageSource} is a <b>hard requirement</b> and thus should be provided.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     * <p>
     * Will validate that the {@link SubscribableMessageSource} and {@link SpringMessageConverter} and {@link EventBus} are not {@code null},
     * and will throw an {@link AxonConfigurationException} if for either of them this holds.
     *
     * @param builder the {@link SpringStreamMessageProcessor.Builder} used to instantiate a {@link SpringStreamMessageProcessor} instance
     */
    protected SpringStreamMessageProcessor(SpringStreamMessageProcessor.Builder builder) {
        builder.validate();
        this.filter = builder.filter;
        this.messageSource = builder.messageSource;
        this.converter = builder.converter;
        if (builder.eventBus != null) {
            this.messageProcessors.addAll(singletonList(builder.eventBus::publish));
        }
        // Subscribes this event message handler to the message source.
        this.messageSource.subscribe(this::handle);
    }

    /**
     * Instantiate a Builder to be able to create a {@link SpringStreamMessageProcessor}.
     * The {@link SubscribableMessageSource} is a <b>hard requirement</b> and as such should be provided.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     *
     * @return a Builder to be able to create a {@link SpringStreamMessageProcessor}.
     */
    public static SpringStreamMessageProcessor.Builder builder() {
        return new SpringStreamMessageProcessor.Builder();
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);
        return () -> messageProcessors.remove(messageProcessor);
    }

    /**
     * If allows by the filter, wraps the given {@link EventMessage} in a {@link GenericMessage} ands sends it to the
     * configured {@link MessageChannel}.
     *
     * @param events the events to handle
     */
    protected void handle(List<? extends EventMessage<?>> events) {
        events.stream()
                .filter(filter)
                .forEach(event -> this.sendOutput(converter.createSpringMessage(event), null, false));
    }

    /**
     * Handles the given {@link Message}. If the filter refuses the message, it is ignored.
     *
     * @param message The message containing the event to publish
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
     * Set filter to remove filtered messages
     *
     * @param filter
     */
    public void setFilter(Predicate<? super EventMessage<?>> filter) {
        this.filter = filter;
    }

    /**
     * Builder class to instantiate a {@link SpringStreamMessageProcessor}.
     * The {@link SubscribableMessageSource} is a <b>hard requirement</b> and thus should be provided.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     */
    public static class Builder {

        private SubscribableMessageSource<EventMessage<?>> messageSource;
        private EventBus eventBus;
        private Predicate<? super EventMessage<?>> filter = m -> true;
        private SpringMessageConverter converter;

        /**
         * Sets the filter to send specific event messages.
         *
         * @param filter The filter to send filtered event messages
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessageProcessor.Builder filter(Predicate<? super EventMessage<?>> filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Sets the messageSource for subscribing handler.
         *
         * @param messageSource The messageSource to subscribe
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessageProcessor.Builder messageSource(SubscribableMessageSource<EventMessage<?>> messageSource) {
            assertNonNull(messageSource, "messageSource may not be null");
            this.messageSource = messageSource;
            return this;
        }

        /**
         * Sets the eventBus to publish received event message.
         *
         * @param eventBus The eventBus to publish event messages
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessageProcessor.Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        /**
         * Sets the converter to convert spring messages to event messages and versa.
         *
         * @param converter The converter to convert messages
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessageProcessor.Builder converter(SpringMessageConverter converter) {
            assertNonNull(converter, "converter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Initializes a {@link SpringStreamMessageProcessor} as specified through this Builder.
         *
         * @return a {@link SpringStreamMessageProcessor} as specified through this Builder
         */
        public SpringStreamMessageProcessor build() {
            return new SpringStreamMessageProcessor(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's specifications
         */
        protected void validate() {
            assertNonNull(messageSource, "The MessageSource is a hard requirement and should be provided");
            assertNonNull(converter, "The Converter is a hard requirement and should be provided");
        }
    }

}
