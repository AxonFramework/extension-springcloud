/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.extensions.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.commandhandling.distributed.CommandDispatchException;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.common.DirectExecutor;
import org.axonframework.lifecycle.ShutdownInProgressException;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.RemoteExceptionDescription;
import org.axonframework.messaging.RemoteHandlingException;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonMap;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SpringHttpCommandBusConnector}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(MockitoExtension.class)
class SpringHttpCommandBusConnectorTest {

    private static final String MEMBER_NAME = "memberName";
    private static final URI ENDPOINT = URI.create("endpoint");
    private static final Member DESTINATION = new SimpleMember<>(MEMBER_NAME, ENDPOINT, false, null);
    private static final CommandMessage<String> COMMAND_MESSAGE =
            new GenericCommandMessage<>("command", singletonMap("commandKey", "commandValue"));

    private static final CommandResultMessage<String> COMMAND_RESULT =
            new GenericCommandResultMessage<>("result", singletonMap("commandResultKey", "CommandResultValue"));
    private static final Exception COMMAND_ERROR = new Exception("oops");
    private static final Exception COMMAND_EXECUTION_ERROR =
            new CommandExecutionException("oops", new RuntimeException("Stub"), "details");

    private static final boolean EXPECT_REPLY = true;

    private SpringHttpCommandBusConnector testSubject;

    private CommandBus localCommandBus;
    private RestTemplate restTemplate;
    private Serializer serializer;
    private Executor executor = new TestExecutor();
    private TestSpanFactory spanFactory;

    private URI expectedUri;
    @Mock
    private CommandCallback<String, String> commandCallback;
    @Mock
    private MessageHandler<? super CommandMessage<?>> messageHandler;

    @BeforeEach
    void setUp() throws Exception {
        serializer = spy(JacksonSerializer.defaultSerializer());
        expectedUri = new URI(ENDPOINT.getScheme(),
                              ENDPOINT.getUserInfo(),
                              ENDPOINT.getHost(),
                              ENDPOINT.getPort(),
                              ENDPOINT.getPath() + "/spring-command-bus-connector/command",
                              null,
                              null);

        localCommandBus = mock(CommandBus.class);
        restTemplate = mock(RestTemplate.class);
        executor = spy(new TestExecutor());
        spanFactory = new TestSpanFactory();

        testSubject = SpringHttpCommandBusConnector.builder()
                                                   .localCommandBus(localCommandBus)
                                                   .restOperations(restTemplate)
                                                   .serializer(serializer)
                                                   .executor(executor)
                                                   .spanFactory(spanFactory)
                                                   .build();
        testSubject.start();
    }

    @Test
    void testSendWithoutCallbackSucceeds() {
        testSubject.send(DESTINATION, COMMAND_MESSAGE);

        verify(executor).execute(any());
        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);

        HttpEntity<SpringHttpDispatchMessage<String>> expectedHttpEntity =
                new HttpEntity<>(buildDispatchMessage(false));
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST),
                                      eq(expectedHttpEntity), argThat(new ParameterizedTypeReferenceMatcher<>()));
    }

    @Test
    void testSendWithoutCallbackThrowsExceptionForMissingDestinationURI() {
        SimpleMember<String> faultyDestination = new SimpleMember<>(MEMBER_NAME, null, false, null);
        assertThrows(IllegalArgumentException.class, () -> testSubject.send(faultyDestination, COMMAND_MESSAGE));
        verify(executor).execute(any());
    }

    @Test
    void testStopSendingCommands() throws InterruptedException, ExecutionException, TimeoutException {
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(), COMMAND_RESULT, serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri),
                                   eq(HttpMethod.POST),
                                   any(),
                                   argThat(new ParameterizedTypeReferenceMatcher<String>()))
        ).thenAnswer(invocationOnMock -> {
            Thread.sleep(200);
            return testResponseEntity;
        });
        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        testSubject.initiateShutdown().get(400, TimeUnit.MILLISECONDS);

        try {
            testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);
            fail("Expected sending new commands to fail once connector is stopped.");
        } catch (ShutdownInProgressException e) {
            // expected
        }

        //noinspection unchecked
        ArgumentCaptor<CommandMessage<? extends String>> commandMessageArgumentCaptor =
                ArgumentCaptor.forClass(CommandMessage.class);
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<? extends String>> commandResultMessageArgumentCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandCallback).onResult(commandMessageArgumentCaptor.capture(),
                                         commandResultMessageArgumentCaptor.capture());
        assertEquals(COMMAND_MESSAGE.getMetaData(), commandMessageArgumentCaptor.getValue().getMetaData());
        assertEquals(COMMAND_MESSAGE.getPayload(), commandMessageArgumentCaptor.getValue().getPayload());
        assertEquals(COMMAND_RESULT.getMetaData(), commandResultMessageArgumentCaptor.getValue().getMetaData());
        assertEquals(COMMAND_RESULT.getPayload(), commandResultMessageArgumentCaptor.getValue().getPayload());
    }

    @Test
    void testSendWithCallbackSucceedsAndReturnsSucceeded() {
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(), COMMAND_RESULT, serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri),
                                   eq(HttpMethod.POST),
                                   any(),
                                   argThat(new ParameterizedTypeReferenceMatcher<String>()))
        ).thenReturn(testResponseEntity);

        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        verify(executor).execute(any());
        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);

        HttpEntity<SpringHttpDispatchMessage<String>> expectedHttpEntity =
                new HttpEntity<>(buildDispatchMessage(EXPECT_REPLY));
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                                      argThat(new ParameterizedTypeReferenceMatcher<>()));

        SerializedObject<byte[]> serializedPayload = serializer.serialize(COMMAND_RESULT.getPayload(), byte[].class);
        SerializedObject<byte[]> serializedMetaData = serializer.serialize(COMMAND_RESULT.getMetaData(), byte[].class);
        SerializedObject<byte[]> serializedException = serializer.serialize(COMMAND_RESULT.optionalExceptionResult()
                                                                                          .orElse(null), byte[].class);
        //noinspection unchecked
        ArgumentCaptor<SerializedObject<byte[]>> serializedObjectCaptor =
                ArgumentCaptor.forClass(SerializedObject.class);
        verify(serializer, times(3)).deserialize(serializedObjectCaptor.capture());

        assertEquals(serializedPayload.getType(), serializedObjectCaptor.getAllValues().get(0).getType());
        assertEquals(serializedPayload.getContentType(), serializedObjectCaptor.getAllValues().get(0).getContentType());
        assertArrayEquals(serializedPayload.getData(), serializedObjectCaptor.getAllValues().get(0).getData());

        assertEquals(serializedException.getType(), serializedObjectCaptor.getAllValues().get(1).getType());
        assertEquals(serializedException.getContentType(),
                     serializedObjectCaptor.getAllValues().get(1).getContentType());
        assertArrayEquals(serializedException.getData(), serializedObjectCaptor.getAllValues().get(1).getData());

        assertEquals(serializedMetaData.getType(), serializedObjectCaptor.getAllValues().get(2).getType());
        assertEquals(serializedMetaData.getContentType(),
                     serializedObjectCaptor.getAllValues().get(2).getContentType());
        assertArrayEquals(serializedMetaData.getData(), serializedObjectCaptor.getAllValues().get(2).getData());

        //noinspection unchecked
        ArgumentCaptor<CommandMessage<? extends String>> commandMessageArgumentCaptor =
                ArgumentCaptor.forClass(CommandMessage.class);
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<? extends String>> commandResultMessageArgumentCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandCallback).onResult(commandMessageArgumentCaptor.capture(),
                                         commandResultMessageArgumentCaptor.capture());
        assertEquals(COMMAND_MESSAGE.getMetaData(), commandMessageArgumentCaptor.getValue().getMetaData());
        assertEquals(COMMAND_MESSAGE.getPayload(), commandMessageArgumentCaptor.getValue().getPayload());
        assertEquals(COMMAND_RESULT.getMetaData(), commandResultMessageArgumentCaptor.getValue().getMetaData());
        assertEquals(COMMAND_RESULT.getPayload(), commandResultMessageArgumentCaptor.getValue().getPayload());
    }

    @Test
    void testSendWithCallbackSucceedsAndReturnsFailed() {
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(),
                                             asCommandResultMessage(COMMAND_ERROR),
                                             serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri),
                                   eq(HttpMethod.POST),
                                   any(),
                                   argThat(new ParameterizedTypeReferenceMatcher<String>()))
        ).thenReturn(testResponseEntity);

        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        verify(executor).execute(any());
        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);

        HttpEntity<SpringHttpDispatchMessage<String>> expectedHttpEntity =
                new HttpEntity<>(buildDispatchMessage(EXPECT_REPLY));
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                                      argThat(new ParameterizedTypeReferenceMatcher<>()));
        SerializedObject<byte[]> serializedObject =
                serializer.serialize(RemoteExceptionDescription.describing(COMMAND_ERROR), byte[].class);
        //noinspection unchecked
        ArgumentCaptor<SerializedObject<byte[]>> serializedObjectCaptor =
                ArgumentCaptor.forClass(SerializedObject.class);
        verify(serializer, times(3)).deserialize(serializedObjectCaptor.capture());

        assertArrayEquals("null".getBytes(), serializedObjectCaptor.getAllValues().get(0).getData());

        assertEquals(serializedObject.getType(), serializedObjectCaptor.getAllValues().get(1).getType());
        assertEquals(serializedObject.getContentType(), serializedObjectCaptor.getAllValues().get(1).getContentType());
        assertArrayEquals(serializedObject.getData(), serializedObjectCaptor.getAllValues().get(1).getData());

        assertArrayEquals("{}".getBytes(), serializedObjectCaptor.getAllValues().get(2).getData());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<CommandResultMessage<String>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandCallback).onResult(eq(COMMAND_MESSAGE), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
    }

    @Test
    void testSendWithCallbackSucceedsAndFailsOnParsingResultMessage() {
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(),
                                             asCommandResultMessage(COMMAND_ERROR),
                                             serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri),
                                   eq(HttpMethod.POST),
                                   any(),
                                   argThat(new ParameterizedTypeReferenceMatcher<String>()))
        ).thenReturn(testResponseEntity);
        doThrow(new RuntimeException("Mocking deserialization exception")).when(serializer).deserialize(any());

        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        verify(executor).execute(any());
        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);

        HttpEntity<SpringHttpDispatchMessage<String>> expectedHttpEntity =
                new HttpEntity<>(buildDispatchMessage(EXPECT_REPLY));
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                                      argThat(new ParameterizedTypeReferenceMatcher<>()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<CommandResultMessage<String>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandCallback).onResult(eq(COMMAND_MESSAGE), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(CommandDispatchException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void testSendWithCallbackThrowsExceptionForMissingDestinationURI() {
        SimpleMember<String> faultyDestination = new SimpleMember<>(MEMBER_NAME, null, false, null);
        AtomicReference<CommandResultMessage<?>> result = new AtomicReference<>();
        testSubject.send(faultyDestination, COMMAND_MESSAGE, (c, r) -> result.set(r));
        verify(executor).execute(any());
        assertTrue(result.get().exceptionResult() instanceof CommandDispatchException);
        assertTrue(result.get().exceptionResult().getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testSubscribeSubscribesCommandHandlerForCommandNameToLocalCommandBus() {
        String expectedCommandName = "commandName";

        testSubject.subscribe(expectedCommandName, messageHandler);

        verify(localCommandBus).subscribe(expectedCommandName, messageHandler);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testReceiveCommandHandlesCommandWithCallbackSucceedsAndCallsSuccess() throws Exception {
        doAnswer(a -> {
            SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback<String, String> callback =
                    (SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback<String, String>) a.getArguments()[1];
            callback.onResult(COMMAND_MESSAGE, COMMAND_RESULT);
            return a;
        }).when(localCommandBus).dispatch(any(), any());

        SpringHttpReplyMessage<String> result =
                (SpringHttpReplyMessage<String>) testSubject.receiveCommand(buildDispatchMessage(EXPECT_REPLY)).get();

        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        CommandResultMessage<String> commandResultMessage = result.getCommandResultMessage(serializer);
        assertFalse(commandResultMessage.isExceptional());
        assertEquals(COMMAND_RESULT.getPayload(), commandResultMessage.getPayload());
        assertEquals(COMMAND_RESULT.getMetaData(), commandResultMessage.getMetaData());

        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    void receiveCommandRepliesWithRemoteHandlerExceptionOnDeserializingCommand()
            throws ExecutionException, InterruptedException {
        String testExceptionMessage = "serialization failure";
        SpringHttpDispatchMessage<Object> testMessage = buildDispatchMessage(EXPECT_REPLY);
        Serializer workingSerializer = JacksonSerializer.defaultSerializer();

        doThrow(new RuntimeException(testExceptionMessage)).when(serializer).deserialize(any());

        //noinspection unchecked
        SpringHttpReplyMessage<String> result =
                (SpringHttpReplyMessage<String>) testSubject.receiveCommand(testMessage).get();

        CommandResultMessage<String> commandResultMessage = result.getCommandResultMessage(workingSerializer);
        assertEquals("UNKNOWN", result.getCommandIdentifier());
        assertTrue(commandResultMessage.isExceptional());
        assertFalse(commandResultMessage.exceptionDetails().isPresent());
        assertEquals(
                "The remote handler threw an exception",
                commandResultMessage.exceptionResult().getMessage()
        );
        assertTrue(commandResultMessage.exceptionResult().getCause().getMessage().contains(testExceptionMessage));

        spanFactory.verifyNoSpan("SpringHttpCommandBusConnector.handle");
    }

    @Test
    void receiveCommandRepliesWithSerializationExceptionOnDeserializingCommand() {
        String testExceptionMessage = "serialization failure";
        SpringHttpDispatchMessage<Object> testMessage = buildDispatchMessage(false);

        doThrow(new RuntimeException(testExceptionMessage)).when(serializer).deserialize(any());

        CompletableFuture<?> result = testSubject.receiveCommand(testMessage);
        assertTrue(result.isCompletedExceptionally());
        try {
            result.get();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains(testExceptionMessage));
        }

        spanFactory.verifyNoSpan("SpringHttpCommandBusConnector.handle");
    }

    @Test
    void receiveCommandHandlesCommandWithCallbackSucceedsAndCallsFailure() throws Exception {
        doAnswer(a -> {
            //noinspection unchecked
            SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback<String, String> callback =
                    (SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback<String, String>) a.getArguments()[1];
            callback.onResult(COMMAND_MESSAGE, asCommandResultMessage(COMMAND_EXECUTION_ERROR));
            return a;
        }).when(localCommandBus).dispatch(any(), any());

        //noinspection unchecked
        SpringHttpReplyMessage<String> result =
                (SpringHttpReplyMessage<String>) testSubject.receiveCommand(buildDispatchMessage(EXPECT_REPLY)).get();

        CommandResultMessage<String> commandResultMessage = result.getCommandResultMessage(serializer);
        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertTrue(commandResultMessage.isExceptional());
        assertTrue(commandResultMessage.exceptionDetails().isPresent());
        assertEquals("details", commandResultMessage.exceptionDetails().get());
        assertEquals(
                "The remote handler threw an exception",
                commandResultMessage.exceptionResult().getMessage()
        );

        assertTrue(
                ((RemoteHandlingException) commandResultMessage.exceptionResult().getCause())
                        .getExceptionDescriptions()
                        .stream()
                        .anyMatch(m -> m.contains(COMMAND_ERROR.getMessage()))
        );

        verify(localCommandBus).dispatch(any(), any());
        spanFactory.verifySpanCompleted("SpringHttpCommandBusConnector.handle");
    }

    @Test
    void receiveCommandHandlesCommandWithCallbackFails() throws Exception {
        doThrow(RuntimeException.class).when(localCommandBus).dispatch(any(), any());

        //noinspection unchecked
        SpringHttpReplyMessage<String> result =
                (SpringHttpReplyMessage<String>) testSubject.receiveCommand(buildDispatchMessage(EXPECT_REPLY)).get();

        CommandResultMessage<String> commandResultMessage = result.getCommandResultMessage(serializer);
        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertTrue(commandResultMessage.isExceptional());

        verify(localCommandBus).dispatch(any(), any());
        spanFactory.verifySpanCompleted("SpringHttpCommandBusConnector.handle");
        spanFactory.verifySpanHasException("SpringHttpCommandBusConnector.handle", RuntimeException.class);
    }

    @Test
    void receiveCommandHandlesCommandWithoutCallback() throws Exception {
        String result = (String) testSubject.receiveCommand(buildDispatchMessage(false)).get();

        assertEquals("", result);

        verify(localCommandBus).dispatch(any());
        spanFactory.verifySpanCompleted("SpringHttpCommandBusConnector.handle");
    }

    @Test
    void receiveCommandHandlesCommandWithoutCallbackThrowsException() throws Exception {
        doThrow(RuntimeException.class).when(localCommandBus).dispatch(any());

        //noinspection unchecked
        SpringHttpReplyMessage<String> result =
                (SpringHttpReplyMessage<String>) testSubject.receiveCommand(buildDispatchMessage(false)).get();

        CommandResultMessage<String> commandResultMessage = result.getCommandResultMessage(serializer);
        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertTrue(commandResultMessage.isExceptional());

        verify(localCommandBus).dispatch(any());
        spanFactory.verifySpanCompleted("SpringHttpCommandBusConnector.handle");
        spanFactory.verifySpanHasException("SpringHttpCommandBusConnector.handle", RuntimeException.class);
    }

    @Test
    void testSendWithCallbackToLocalMember() {
        SimpleMember<String> localDestination = new SimpleMember<>(MEMBER_NAME, null, true, null);
        testSubject.send(localDestination, COMMAND_MESSAGE, new NoOpCallback());

        verifyNoMoreInteractions(restTemplate);
        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    void testSendWithoutCallbackToLocalMember() {
        SimpleMember<String> localDestination = new SimpleMember<>(MEMBER_NAME, null, true, null);
        testSubject.send(localDestination, COMMAND_MESSAGE);

        verifyNoMoreInteractions(restTemplate);
        verify(localCommandBus).dispatch(any());
    }

    @Test
    void testLocalSegmentReturnsExpectedCommandBus() {
        Optional<CommandBus> result = testSubject.localSegment();
        assertTrue(result.isPresent());
        assertEquals(localCommandBus, result.get());
    }

    private <C> SpringHttpDispatchMessage<C> buildDispatchMessage(boolean expectReply) {
        return new SpringHttpDispatchMessage<>(COMMAND_MESSAGE, serializer, expectReply);
    }

    private static class ParameterizedTypeReferenceMatcher<R> implements
            ArgumentMatcher<ParameterizedTypeReference<SpringHttpReplyMessage<R>>> {

        private final ParameterizedTypeReference<SpringHttpReplyMessage<R>> expected =
                new ParameterizedTypeReference<SpringHttpReplyMessage<R>>() {
                };

        @Override
        public boolean matches(ParameterizedTypeReference<SpringHttpReplyMessage<R>> actual) {
            return actual != null &&
                    actual.getType().getTypeName()
                          .equals(expected.getType().getTypeName());
        }
    }

    private static class TestExecutor implements Executor {

        private final Executor executor = DirectExecutor.INSTANCE;

        @Override
        public void execute(@SuppressWarnings("NullableProblems") Runnable command) {
            executor.execute(command);
        }
    }
}
