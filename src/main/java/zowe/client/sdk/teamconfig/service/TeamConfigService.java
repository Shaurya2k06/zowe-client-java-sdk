/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package zowe.client.sdk.teamconfig.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zowe.client.sdk.teamconfig.exception.TeamConfigException;
import zowe.client.sdk.teamconfig.keytar.KeyTarConfig;
import zowe.client.sdk.teamconfig.model.ConfigContainer;
import zowe.client.sdk.teamconfig.model.Partition;
import zowe.client.sdk.teamconfig.model.Profile;
import zowe.client.sdk.teamconfig.types.SectionType;
import zowe.client.sdk.utility.JsonUtils;
import zowe.client.sdk.utility.ValidateUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * TeamConfigService class that provides a service layer to perform Zowe Global Team Configuration processing.
 *
 * @author Frank Giordano
 * @version 7.0
 */
public class TeamConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(TeamConfigService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create a new TeamConfigService instance.
     */
    public TeamConfigService() {
        // intentionally empty
    }

    /**
     * Parse a JSON representation of a Zowe Global Team Configuration partition section.
     *
     * @param name       partition name
     * @param jsonObject ObjectNode object
     * @return Partition object
     * @author Frank Giordano
     */
    private Partition getPartition(final String name, final ObjectNode jsonObject) throws TeamConfigException {
        final List<Profile> profiles = new ArrayList<>();
        Map<String, String> properties = new HashMap<>();
        LOG.debug("partition found name {} containing {}:", name, jsonObject);
        final Iterator<Map.Entry<String, JsonNode>> fields = jsonObject.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> entry = fields.next();
            final String keyObj = entry.getKey();
            if (SectionType.PROFILES.getValue().equals(keyObj)) {
                final ObjectNode jsonProfileObj = (ObjectNode) entry.getValue();
                final Iterator<Map.Entry<String, JsonNode>> profileFields = jsonProfileObj.fields();
                while (profileFields.hasNext()) {
                    final Map.Entry<String, JsonNode> profileEntry = profileFields.next();
                    final String profileKeyVal = profileEntry.getKey();
                    final ObjectNode profileTypeJsonObj = (ObjectNode) profileEntry.getValue();
                    final ObjectNode propsNode = profileTypeJsonObj.has("properties")
                            ? (ObjectNode) profileTypeJsonObj.get("properties") : objectMapper.createObjectNode();
                    final ArrayNode secureNode = profileTypeJsonObj.has("secure")
                            ? (ArrayNode) profileTypeJsonObj.get("secure") : null;
                    profiles.add(new Profile(profileKeyVal,
                            profileTypeJsonObj.has("type") ? profileTypeJsonObj.get("type").asText() : null,
                            propsNode,
                            secureNode));
                }
            } else if ("properties".equalsIgnoreCase(keyObj)) {
                try {
                    properties = JsonUtils.parseMap((ObjectNode) entry.getValue());
                } catch (JsonProcessingException e) {
                    throw new TeamConfigException("Error parsing properties", e);
                }
            }
        }
        return new Partition(name, properties, profiles);
    }

    /**
     * Return ConfigContainer object container of a parsed Zowe Global Team Configuration file representation.
     *
     * @param config KeyTarConfig object
     * @return ConfigContainer object
     * @throws TeamConfigException error processing team configuration
     * @author Frank Giordano
     */
    public ConfigContainer getTeamConfig(final KeyTarConfig config) throws TeamConfigException {
        ValidateUtils.checkNullParameter(config, "config");
        final JsonNode root;
        try {
            root = objectMapper.readTree(new File(config.getLocation()));
        } catch (IOException e) {
            throw new TeamConfigException("Error reading zowe global team configuration file", e);
        }
        return parseJson((ObjectNode) root);
    }

    /**
     * Determine if JSON contains a partition section next.
     *
     * @param profileKeyObj partition name
     * @return boolean true or false
     * @author Frank Giordano
     */
    private boolean isPartition(final ObjectNode profileTypeJsonObj) {
        final Iterator<String> fieldNames = profileTypeJsonObj.fieldNames();
        if (fieldNames.hasNext()) {
            String keyVal = fieldNames.next();
            return SectionType.PROFILES.getValue().equals(keyVal);
        } else {
            throw new IllegalStateException("TeamConfig profile type detail missing in profile section.");
        }
    }

    /**
     * Parse a JSON representation of a Zowe Global Team Configuration file.
     *
     * @param jsonObj ObjectNode object
     * @return ConfigContainer object
     * @author Frank Giordano
     */
    private ConfigContainer parseJson(final ObjectNode jsonObj) throws TeamConfigException {
        String schema = null;
        Boolean autoStore = null;
        final List<Profile> profiles = new ArrayList<>();
        final Map<String, String> defaults = new HashMap<>();
        final List<Partition> partitions = new ArrayList<>();

        final Iterator<Map.Entry<String, JsonNode>> sectionFields = jsonObj.fields();
        while (sectionFields.hasNext()) {
            final Map.Entry<String, JsonNode> sectionEntry = sectionFields.next();
            final String keySectionVal = sectionEntry.getKey();
            if (SectionType.$SCHEMA.getValue().equals(keySectionVal)) {
                schema = sectionEntry.getValue().asText();
            } else if (SectionType.PROFILES.getValue().equals(keySectionVal)) {
                final ObjectNode jsonProfileObj = (ObjectNode) sectionEntry.getValue();
                final Iterator<Map.Entry<String, JsonNode>> profileFields = jsonProfileObj.fields();
                while (profileFields.hasNext()) {
                    final Map.Entry<String, JsonNode> profileEntry = profileFields.next();
                    final String profileKeyVal = profileEntry.getKey();
                    final ObjectNode profileTypeJsonObj = (ObjectNode) profileEntry.getValue();
                    if (isPartition(profileTypeJsonObj)) {
                        partitions.add(getPartition(profileKeyVal, profileTypeJsonObj));
                    } else {
                        final ObjectNode propsNode = profileTypeJsonObj.has("properties")
                                ? (ObjectNode) profileTypeJsonObj.get("properties") : objectMapper.createObjectNode();
                        final ArrayNode secureNode = profileTypeJsonObj.has("secure")
                                ? (ArrayNode) profileTypeJsonObj.get("secure") : null;
                        profiles.add(new Profile(profileKeyVal,
                                profileTypeJsonObj.has("type") ? profileTypeJsonObj.get("type").asText() : null,
                                propsNode,
                                secureNode));
                    }
                }
            } else if (SectionType.DEFAULTS.getValue().equals(keySectionVal)) {
                final ObjectNode keyValues = (ObjectNode) sectionEntry.getValue();
                keyValues.fields().forEachRemaining(e -> defaults.put(e.getKey(), e.getValue().asText()));
            } else if (SectionType.AUTOSTORE.getValue().equals(keySectionVal)) {
                autoStore = sectionEntry.getValue().asBoolean();
            }
        }

        return new ConfigContainer(partitions, schema, profiles, defaults, autoStore);
    }

}
