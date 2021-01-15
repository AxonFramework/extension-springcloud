/*
 * Copyright (c) 2010-2020. Axon Framework
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.beans.ConstructorProperties;

/**
 * Holder of the {@code loadFactor} and the serialized {@link CommandMessageFilter} of a specific member. Allows for
 * easier data transfer. Both the {@code loadFactor} and {@code CommandMessageFilter} can be used to recreate a {@link
 * MemberCapabilities} object. The serialized {@code CommandMessageFilter} is kept as a {@link String} of the contents
 * and a {@code String} defining the class type.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class SerializedMemberCapabilities {

    private final int loadFactor;
    private final String serializedCommandFilter;
    private final String serializedCommandFilterType;

    /**
     * Build a {@link SerializedMemberCapabilities} based on the given {@code delegate}, serializing the {@link
     * CommandMessageFilter} with the given {@code serializer}.
     *
     * @param delegate   the delegate {@link MemberCapabilities} to base this {@link SerializedMemberCapabilities}
     *                   instance on
     * @param serializer the {@link Serializer} to serialize the {@link MemberCapabilities#getCommandFilter()} with
     */
    public static SerializedMemberCapabilities build(MemberCapabilities delegate, Serializer serializer) {
        SerializedObject<String> serializedCommandFilter =
                serializer.serialize(delegate.getCommandFilter(), String.class);
        return new SerializedMemberCapabilities(delegate.getLoadFactor(),
                                                serializedCommandFilter.getData(),
                                                serializedCommandFilter.getType().getName());
    }

    /**
     * Construct a {@link SerializedMemberCapabilities} based on the serialized format of the {@link
     * CommandMessageFilter}.
     *
     * @param loadFactor                  the load factor for a given member
     * @param serializedCommandFilter     the serialized {@link CommandMessageFilter} for a given member
     * @param serializedCommandFilterType the serialized type of the {@link CommandMessageFilter} for a given member
     */
    @JsonCreator
    @ConstructorProperties({"loadFactor", "serializedCommandFilter", "serializedCommandFilterType"})
    public SerializedMemberCapabilities(@JsonProperty("loadFactor") int loadFactor,
                                        @JsonProperty("serializedCommandFilter") String serializedCommandFilter,
                                        @JsonProperty("serializedCommandFilterType") String serializedCommandFilterType) {
        this.loadFactor = loadFactor;
        this.serializedCommandFilter = serializedCommandFilter;
        this.serializedCommandFilterType = serializedCommandFilterType;
    }

    /**
     * Returns the load factor of a member.
     *
     * @return the load factor of a member
     */
    public int getLoadFactor() {
        return loadFactor;
    }

    /**
     * Returns the serialized format of a {@link CommandMessageFilter} for a member.
     *
     * @return the serialized format of a {@link CommandMessageFilter} for a member
     */
    public String getSerializedCommandFilter() {
        return serializedCommandFilter;
    }

    /**
     * Returns the type of a serialized {@link CommandMessageFilter} for a member.
     *
     * @return the type of a serialized {@link CommandMessageFilter} for a member
     */
    public String getSerializedCommandFilterType() {
        return serializedCommandFilterType;
    }
}
