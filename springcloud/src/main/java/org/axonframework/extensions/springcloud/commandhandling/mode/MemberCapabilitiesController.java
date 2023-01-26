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

package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.common.AxonConfigurationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Controller towards sharing the {@link MemberCapabilities} (as a {@link SerializedMemberCapabilities}) of a {@link
 * RestCapabilityDiscoveryMode}.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
@RestController
@RequestMapping("${axon.distributed.spring-cloud.rest-mode-url:/member-capabilities}")
public class MemberCapabilitiesController {

    private final RestCapabilityDiscoveryMode restCapabilityDiscoveryMode;

    /**
     * Instantiate a {@link Builder} to be able to create a {@link MemberCapabilitiesController}.
     * <p>
     * The {@link RestCapabilityDiscoveryMode} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a {@link Builder} to be able to create a {@link MemberCapabilitiesController}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link MemberCapabilitiesController} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link RestCapabilityDiscoveryMode} is not {@code null} and will throw an {@link
     * AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MemberCapabilitiesController} instance
     */
    protected MemberCapabilitiesController(Builder builder) {
        builder.validate();
        this.restCapabilityDiscoveryMode = builder.restCapabilityDiscoveryMode;
    }

    /**
     * Returns the {@link SerializedMemberCapabilities} from a {@link RestCapabilityDiscoveryMode} by invoking the
     * {@link RestCapabilityDiscoveryMode#getLocalMemberCapabilities()}.
     * <p>
     * Can either be called directly or through a GET operation on this controllers main request mapping.
     *
     * @return the {@link SerializedMemberCapabilities} if the node this CommandRouter implementation is part of
     */
    @GetMapping
    public SerializedMemberCapabilities getLocalMemberCapabilities() {
        return restCapabilityDiscoveryMode.getLocalMemberCapabilities();
    }

    /**
     * Builder class to instantiate an {@link MemberCapabilitiesController}.
     * <p>
     * The {@link RestCapabilityDiscoveryMode} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder {

        private RestCapabilityDiscoveryMode restCapabilityDiscoveryMode;

        /**
         * Specify the {@link RestCapabilityDiscoveryMode} used by this controller to return {@link MemberCapabilities}
         * through.
         *
         * @param restCapabilityDiscoveryMode a {@link RestCapabilityDiscoveryMode} used by this controller to return
         *                                    {@link MemberCapabilities} through
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder restCapabilityDiscoveryMode(RestCapabilityDiscoveryMode restCapabilityDiscoveryMode) {
            assertNonNull(restCapabilityDiscoveryMode, "RestCapabilityDiscoveryMode may not be null");
            this.restCapabilityDiscoveryMode = restCapabilityDiscoveryMode;
            return this;
        }

        /**
         * Initializes a {@link MemberCapabilitiesController} implementation as specified through this Builder.
         *
         * @return a {@link MemberCapabilitiesController} implementation as specified through this Builder
         */
        public MemberCapabilitiesController build() {
            return new MemberCapabilitiesController(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        public void validate() {
            assertNonNull(
                    restCapabilityDiscoveryMode,
                    "The RestCapabilityDiscoveryMode is a hard requirement and should be provided"
            );
        }
    }
}
