package org.axonframework.extensions.springcloud.eventhandling;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.springcloud.eventhandling.converter.DefaultSpringMessageEventMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageEventMessageConverter;
import org.axonframework.messaging.SubscribableMessageSource;
import org.springframework.integration.handler.AbstractMessageProducingHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Collections.singletonList;

/**
 * @author Mehdi chitforoosh
 * @since 4.1
 */
public class AxonProcessorMessageHandler extends AbstractMessageProducingHandler implements SubscribableMessageSource<EventMessage<?>> {

    private final SubscribableMessageSource<EventMessage<?>> messageSource;
    private final CopyOnWriteArrayList<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();
    private final Predicate<? super EventMessage<?>> filter;
    private final SpringMessageEventMessageConverter eventMessageConverter;

    /**
     * Initialize the adapter to publish all incoming events to the subscribed processors. Note that this instance should
     * be registered as a consumer of a Spring Message Channel.
     */
    public AxonProcessorMessageHandler(EventBus eventBus) {
        this(eventBus, singletonList(eventBus::publish), m -> true, new DefaultSpringMessageEventMessageConverter());
    }

    /**
     * Initialize an AxonProcessorMessageHandler instance that sends all incoming Event Messages to the given
     * {@code eventBus}. It is still possible for other Event Processors to subscribe to this MessageChannelAdapter.
     *
     * @param eventBus The EventBus instance for forward all messages to
     * @param filter   The filter that indicates which messages to forward.
     */
    public AxonProcessorMessageHandler(EventBus eventBus, Predicate<? super EventMessage<?>> filter) {
        this(eventBus, singletonList(eventBus::publish), filter, new DefaultSpringMessageEventMessageConverter());
    }

    /**
     * Initialize the adapter to publish all incoming events to the subscribed processors. Note that this instance should
     * be registered as a consumer of a Spring Message Channel.
     *
     * @param messageSource         The inbound of messages to subscribe to.
     * @param processors            Processors to be subscribed
     * @param filter                The filter that indicates which messages to forward.
     * @param eventMessageConverter The message converter to use to convert spring message into event message
     */
    public AxonProcessorMessageHandler(SubscribableMessageSource<EventMessage<?>> messageSource, List<Consumer<List<? extends EventMessage<?>>>> processors
            , Predicate<? super EventMessage<?>> filter, SpringMessageEventMessageConverter eventMessageConverter) {
        this.messageSource = messageSource;
        this.messageProcessors.addAll(processors);
        this.filter = filter;
        this.eventMessageConverter = eventMessageConverter;
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);
        return () -> messageProcessors.remove(messageProcessor);
    }

    /**
     * Subscribes this event listener to the event bus.
     */
    @Override
    protected void onInit() {
        super.onInit();
        messageSource.subscribe(this::handle);
    }


    /**
     * If allows by the filter, wraps the given {@code event} in a {@link GenericMessage} ands sends it to the
     * configured {@link MessageChannel}.
     *
     * @param events the events to handle
     */
    protected void handle(List<? extends EventMessage<?>> events) {
        events.stream()
                .filter(filter)
                .forEach(event -> this.sendOutput(eventMessageConverter.toSpringMessage(event), null, false));
    }

    /**
     * Handles the given {@code message}. If the filter refuses the message, it is ignored.
     *
     * @param message The message containing the event to publish
     */
    @Override
    protected void handleMessageInternal(Message<?> message) {
        EventMessage<?> eventMessage = eventMessageConverter.toEventMessage(message);
        List<? extends EventMessage<?>> messages = singletonList(eventMessage);
        for (Consumer<List<? extends EventMessage<?>>> messageProcessor : messageProcessors) {
            messageProcessor.accept(messages);
        }
    }

}
