package org.axonframework.extensions.springcloud.eventhandling.outbound;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.springcloud.eventhandling.converter.DefaultSpringMessageEventMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageEventMessageConverter;
import org.axonframework.messaging.SubscribableMessageSource;
import org.springframework.integration.handler.AbstractMessageProducingHandler;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;

/**
 *
 * @author Mehdi Chitforoosh
 * @since 4.1
 */
public class AxonOutboundChannelAdapter extends AbstractMessageProducingHandler implements SubscribableMessageSource<EventMessage<?>> {

    private final CopyOnWriteArrayList<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();
    private final SpringMessageEventMessageConverter eventMessageConverter;

    /**
     * Initialize an AxonOutboundChannelAdapter instance that sends all incoming Event Messages to the given
     * {@code eventBus}. It is still possible for other Event Processors to subscribe to this MessageChannelAdapter.
     *
     * @param eventBus The EventBus instance for forward all messages to
     */
    public AxonOutboundChannelAdapter(EventBus eventBus) {
        this(singletonList(eventBus::publish), new DefaultSpringMessageEventMessageConverter());
    }

    /**
     * Initialize the adapter to publish all incoming events to the subscribed processors. Note that this instance should
     * be registered as a consumer of a Spring Message Channel.
     *
     * @param processors            Processors to be subscribed
     * @param eventMessageConverter The message converter to use to convert spring message into event message
     */
    public AxonOutboundChannelAdapter(List<Consumer<List<? extends EventMessage<?>>>> processors, SpringMessageEventMessageConverter eventMessageConverter) {
        this.messageProcessors.addAll(processors);
        this.eventMessageConverter = eventMessageConverter;
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);
        return () -> messageProcessors.remove(messageProcessor);
    }

    /**
     * Handles the given {@code message}. If the filter refuses the message, it is ignored.
     *
     * @param message The message containing the event to publish
     */
    @Override
    protected void handleMessageInternal(Message<?> message) throws Exception {
        EventMessage<?> eventMessage = eventMessageConverter.toEventMessage(message);
        List<? extends EventMessage<?>> messages = singletonList(eventMessage);
        for (Consumer<List<? extends EventMessage<?>>> messageProcessor : messageProcessors) {
            messageProcessor.accept(messages);
        }
    }

}
