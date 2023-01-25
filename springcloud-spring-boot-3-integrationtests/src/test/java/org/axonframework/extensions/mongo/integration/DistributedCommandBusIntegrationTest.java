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

package org.axonframework.extensions.mongo.integration;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DistributedCommandBusIntegrationTest {

    public static final DockerImageName EUREKA_SERVER_IMAGE =
            DockerImageName.parse("springcloud/eureka:latest");

    private ApplicationContextRunner testApplicationContext;

    @Container
    private static final GenericContainer EUREKA_SERVER_CONTAINER =
            new GenericContainer(EUREKA_SERVER_IMAGE).withExposedPorts(8761);

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=false")
                .withPropertyValues("axon.distributed.enabled=true")
                .withPropertyValues("eureka.client.serviceUrl.defaultZone=http://localhost:"
                                            + EUREKA_SERVER_CONTAINER.getMappedPort(8761) + "/eureka")
                .withPropertyValues("eureka.instance.preferIpAddress=true")
                .withPropertyValues("spring.application.name=test-app")
                .withUserConfiguration(DefaultContext.class);
    }

    @Test
    void willUseADistributedCommandBus() {
        testApplicationContext
                .run(context -> {
                    DiscoveryClient discoveryClient = context.getBean(DiscoveryClient.class);
                    assertNotNull(discoveryClient);
                    DistributedCommandBus commandBus = context.getBean(DistributedCommandBus.class);
                    assertNotNull(commandBus);
                    subscribeCommandHandler(commandBus);
                    executeCommand(commandBus);
                });
    }

    @Test
    void failsWhenNotRegistered() {
        testApplicationContext
                .run(context -> {
                    DiscoveryClient discoveryClient = context.getBean(DiscoveryClient.class);
                    assertNotNull(discoveryClient);
                    DistributedCommandBus commandBus = context.getBean(DistributedCommandBus.class);
                    assertNotNull(commandBus);
                    executeCommandWhileNotRegistered(commandBus);
                });
    }

    private void subscribeCommandHandler(DistributedCommandBus commandBus) {
        commandBus.subscribe("testCommand", e -> "correct");
    }

    private void executeCommand(DistributedCommandBus commandBus) {
        Message message = new GenericMessage("hi");
        CommandMessage command = new GenericCommandMessage(message, "testCommand");
        AtomicReference<String> result = new AtomicReference<>();
        commandBus.dispatch(command, (commandMessage, commandResultMessage) -> {
            result.set((String) commandResultMessage.getPayload());
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> result.get() != null);
        assertEquals("correct", result.get());
    }

    private void executeCommandWhileNotRegistered(DistributedCommandBus commandBus) {
        Message message = new GenericMessage("hi");
        CommandMessage command = new GenericCommandMessage(message, "anotherCommand");
        AtomicReference<Throwable> result = new AtomicReference<>();
        commandBus.dispatch(command, (commandMessage, commandResultMessage) -> {
            result.set(commandResultMessage.exceptionResult());
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> result.get() != null);
        assertTrue(result.get() instanceof NoHandlerForCommandException);
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    public static class DefaultContext {

    }
}
