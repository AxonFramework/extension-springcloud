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

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

/**
 * Default implementation of the {@link MemberCapabilities}, storing the {@code loadFactor} and {@link
 * CommandMessageFilter} as is.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class DefaultMemberCapabilities implements MemberCapabilities {

    private static final long serialVersionUID = -2528152898649473601L;

    /**
     * Defines a member which is incapable of handling anything, by setting the load factor to {@code 0} and the {@link
     * CommandMessageFilter} to {@link DenyAll}.
     */
    public static final MemberCapabilities INCAPABLE_MEMBER = new DefaultMemberCapabilities(0, DenyAll.INSTANCE);

    private final int loadFactor;
    private final CommandMessageFilter commandFilter;

    /**
     * Construct a {@link DefaultMemberCapabilities} based on the given {@code loadFactor} and {@code commandFilter}.
     *
     * @param loadFactor    the load factor for a given member
     * @param commandFilter the {@link CommandMessageFilter} for a given member
     */
    public DefaultMemberCapabilities(int loadFactor, CommandMessageFilter commandFilter) {
        this.loadFactor = loadFactor;
        this.commandFilter = commandFilter;
    }

    /**
     * Build a {@link DefaultMemberCapabilities} based on the given {@code serializedCapabilities}, deserializing the
     * {@link CommandMessageFilter} with the given {@code serializer}.
     *
     * @param serializedCapabilities the {@link SerializedMemberCapabilities} to base this {@link
     *                               DefaultMemberCapabilities} instance on
     * @param serializer             the {@link Serializer} to deserialize the {@link CommandMessageFilter} with for the
     *                               {@link MemberCapabilities#getCommandFilter()} method
     */
    public DefaultMemberCapabilities(SerializedMemberCapabilities serializedCapabilities,
                                     Serializer serializer) {
        this.loadFactor = serializedCapabilities.getLoadFactor();
        this.commandFilter = serializer.deserialize(new SimpleSerializedObject<>(
                serializedCapabilities.getSerializedCommandFilter(),
                String.class,
                serializedCapabilities.getSerializedCommandFilterType(),
                null
        ));
    }

    @Override
    public int getLoadFactor() {
        return loadFactor;
    }

    @Override
    public CommandMessageFilter getCommandFilter() {
        return commandFilter;
    }
}
