/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.extensions.springcloud.autoconfig;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter;
import org.axonframework.extensions.springcloud.commandhandling.SpringHttpCommandBusConnector;
import org.axonframework.extensions.springcloud.commandhandling.mode.AcceptAllCommandsDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.IgnoreListingDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.MemberCapabilitiesController;
import org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SpringCloudAutoConfiguration}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(SpringExtension.class)
class SpringCloudAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(TestContext.class)
                                                               .withPropertyValues("axon.axonserver.enabled=false");
    }

    @Test
    void defaultSpringCloudAutoConfiguration() {
        testApplicationContext.run(context -> {
            assertThat(context).getBeanNames(RoutingStrategy.class)
                               .isEmpty();
            assertThat(context).getBeanNames(RestTemplate.class)
                               .isEmpty();
            assertThat(context).getBeanNames(CapabilityDiscoveryMode.class)
                               .isEmpty();
            assertThat(context).getBeanNames(CommandRouter.class)
                               .isEmpty();
            assertThat(context).getBeanNames(CommandBusConnector.class)
                               .isEmpty();

            assertThat(context).getBeanNames(CommandBus.class)
                               .hasSize(1);
            assertThat(context).getBean(CommandBus.class)
                               .isExactlyInstanceOf(SimpleCommandBus.class);
        });
    }

    @Test
    void enabledSpringCloudAutoConfiguration() {
        testApplicationContext.withPropertyValues("axon.distributed.enabled=true").run(context -> {
            assertThat(context).getBeanNames(RoutingStrategy.class)
                               .hasSize(1);
            assertThat(context).getBean(RoutingStrategy.class)
                               .isExactlyInstanceOf(AnnotationRoutingStrategy.class);

            assertThat(context).getBeanNames(RestTemplate.class)
                               .hasSize(1);
            assertThat(context).getBean(RestTemplate.class)
                               .isExactlyInstanceOf(RestTemplate.class);

            assertThat(context).getBeanNames(RestCapabilityDiscoveryMode.class)
                               .hasSize(1);
            assertThat(context).getBeanNames(CapabilityDiscoveryMode.class)
                               .hasSize(2);
            assertThat(context).getBean(CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(IgnoreListingDiscoveryMode.class);
            assertThat(context).getBean("restCapabilityDiscoveryMode",
                                        CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(RestCapabilityDiscoveryMode.class);

            assertThat(context).getBeanNames(MemberCapabilitiesController.class)
                               .hasSize(1);
            assertThat(context).getBean(MemberCapabilitiesController.class)
                               .isExactlyInstanceOf(MemberCapabilitiesController.class);

            assertThat(context).getBeanNames(CommandRouter.class)
                               .hasSize(1);
            assertThat(context).getBean(CommandRouter.class)
                               .isExactlyInstanceOf(SpringCloudCommandRouter.class);

            assertThat(context).getBeanNames(CommandBusConnector.class)
                               .hasSize(1);
            assertThat(context).getBean(CommandBusConnector.class)
                               .isExactlyInstanceOf(SpringHttpCommandBusConnector.class);

            assertThat(context).getBeanNames(CommandBus.class)
                               .hasSize(2);

            Map<String, CommandBus> commandBuses = context.getBeansOfType(CommandBus.class);
            CommandBus localSegment = commandBuses.get("commandBus");
            assertEquals(SimpleCommandBus.class, localSegment.getClass());
            CommandBus distributedCommandBus = commandBuses.get("distributedCommandBus");
            assertEquals(DistributedCommandBus.class, distributedCommandBus.getClass());
            assertNotEquals(localSegment, distributedCommandBus);
        });
    }

    @Test
    void disablingIgnoreListingOnlyCreatesRestCapabilityDiscoveryMode() {
        testApplicationContext.withPropertyValues(
                "axon.distributed.enabled=true",
                "axon.distributed.spring-cloud.enable-ignore-listing=false"
        ).run(context -> {
            assertThat(context).getBeanNames(CapabilityDiscoveryMode.class)
                               .hasSize(1);
            assertThat(context).getBean(CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(RestCapabilityDiscoveryMode.class);
        });
    }

    @Test
    void disablingIgnoreListingAndAcceptAllOnlyCreatesRestCapabilityDiscoveryMode() {
        testApplicationContext.withPropertyValues(
                "axon.distributed.enabled=true",
                "axon.distributed.spring-cloud.enable-ignore-listing=false",
                "axon.distributed.spring-cloud.enable-accept-all-commands=false"
        ).run(context -> {
            assertThat(context).getBeanNames(CapabilityDiscoveryMode.class)
                               .hasSize(1);
            assertThat(context).getBean(CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(RestCapabilityDiscoveryMode.class);
        });
    }

    @Test
    void enablingIgnoreListingCreatesTwoCapabilityDiscoveryModeInstances() {
        testApplicationContext.withPropertyValues(
                "axon.distributed.enabled=true",
                "axon.distributed.spring-cloud.enable-ignore-listing=true",
                "axon.distributed.spring-cloud.enable-accept-all-commands=false"
        ).run(context -> {
            assertThat(context).getBeanNames(CapabilityDiscoveryMode.class)
                               .hasSize(2);
            assertThat(context).getBean("restCapabilityDiscoveryMode", CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(RestCapabilityDiscoveryMode.class);
            assertThat(context).getBean(CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(IgnoreListingDiscoveryMode.class);
        });
    }

    @Test
    void enablingAcceptAllCreatesTwoCapabilityDiscoveryModeInstances() {
        testApplicationContext.withPropertyValues(
                "axon.distributed.enabled=true",
                "axon.distributed.spring-cloud.enable-ignore-listing=false",
                "axon.distributed.spring-cloud.enable-accept-all-commands=true"
        ).run(context -> {
            assertThat(context).getBeanNames(CapabilityDiscoveryMode.class)
                               .hasSize(2);
            assertThat(context).getBean("restCapabilityDiscoveryMode", CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(RestCapabilityDiscoveryMode.class);
            assertThat(context).getBean(CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(AcceptAllCommandsDiscoveryMode.class);
        });
    }

    @Test
    void enablingIgnoreListingAndAcceptAllCreatesTwoCapabilityDiscoveryModeInstances() {
        testApplicationContext.withPropertyValues(
                "axon.distributed.enabled=true",
                "axon.distributed.spring-cloud.enable-ignore-listing=True",
                "axon.distributed.spring-cloud.enable-accept-all-commands=true"
        ).run(context -> {
            assertThat(context).getBeanNames(CapabilityDiscoveryMode.class)
                               .hasSize(2);
            assertThat(context).getBean("restCapabilityDiscoveryMode", CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(RestCapabilityDiscoveryMode.class);
            assertThat(context).getBean(CapabilityDiscoveryMode.class)
                               .isExactlyInstanceOf(IgnoreListingDiscoveryMode.class);
            CapabilityDiscoveryMode capabilityDiscoveryMode =
                    context.getBean("capabilityDiscoveryMode", CapabilityDiscoveryMode.class);

            assertTrue(capabilityDiscoveryMode.getClass().isAssignableFrom(IgnoreListingDiscoveryMode.class));
            CapabilityDiscoveryMode delegate = getFieldValue(
                    IgnoreListingDiscoveryMode.class.getDeclaredField("delegate"), capabilityDiscoveryMode
            );
            assertTrue(delegate.getClass().isAssignableFrom(AcceptAllCommandsDiscoveryMode.class));
        });
    }

    @EnableAutoConfiguration(exclude = {
            JmxAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceAutoConfiguration.class
    })
    public static class TestContext {

        @Bean
        public DiscoveryClient discoveryClient() {
            return new SimpleDiscoveryClient(new SimpleDiscoveryProperties());
        }

        @Bean
        public Registration registration() {
            return new Registration() {
                @Override
                public String getServiceId() {
                    return "TestServiceId";
                }

                @Override
                public String getHost() {
                    return "localhost";
                }

                @Override
                public int getPort() {
                    return 12345;
                }

                @Override
                public boolean isSecure() {
                    return true;
                }

                @Override
                public URI getUri() {
                    return UriComponentsBuilder.fromUriString("localhost")
                                               .port(12345)
                                               .build()
                                               .toUri();
                }

                @Override
                public Map<String, String> getMetadata() {
                    return new HashMap<>();
                }
            };
        }
    }
}
