/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package zowe.client.sdk.teamconfig.keytar;

import com.starxg.keytar.Keytar;
import com.starxg.keytar.KeytarException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zowe.client.sdk.teamconfig.exception.TeamConfigException;
import zowe.client.sdk.utility.ValidateUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Implementation class for IkeyTar interface that contains the logic for KeyTar processing
 *
 * @author Frank Giordano
 * @version 7.0
 */
public class KeyTarImpl implements IKeyTar {

    private static final Logger LOG = LoggerFactory.getLogger(KeyTarImpl.class);

    /**
     * List of KeyTarConfig objects - OS might contain multiple OS stores
     */
    private final List<KeyTarConfig> keyTarConfigs = new ArrayList<>();

    /**
     * Represents a string value used for KeyTar querying for OS credential store value
     */
    private String serviceName;

    /**
     * Represents a string value used for KeyTar querying for OS credential store value
     */
    private String accountName;

    /**
     * Represents a string value of the retrieved OS credential store
     */
    private String keyString;

    /**
     * Create a new KeyTarImpl instance.
     */
    public KeyTarImpl() {
        // intentionally empty
    }

    /**
     * Return keyString JSON value parsed into a KeyTarConfig object.
     *
     * @return list of KeyTarConfig objects
     * @throws TeamConfigException error processing team configuration
     */
    @Override
    public List<KeyTarConfig> getKeyConfigs() throws TeamConfigException {
        ValidateUtils.checkNullParameter(keyString, "keyString", "perform processKey first");
        ValidateUtils.checkIllegalParameter(keyString.isBlank(), "keyString is empty");
        if (!keyTarConfigs.isEmpty()) {
            return keyTarConfigs;
        }
        return parseJson();
    }

    /**
     * Return a keyString value after KeyTar has been fully processed.
     *
     * @return list of KeyTarConfig objects
     */
    @Override
    public String getKeyTarValue() {
        ValidateUtils.checkNullParameter(keyString, "keyString", "perform processKey first");
        ValidateUtils.checkIllegalParameter(keyString.isBlank(), "keyString is empty");
        return keyString;
    }

    /**
     * Parse KeyTar JSON string returned when querying the OS credential store.
     *
     * @return list of KeyTarConfig objects
     * @throws TeamConfigException error processing team configuration
     * @author Frank Giordano
     */
    @SuppressWarnings("unchecked")
    private List<KeyTarConfig> parseJson() throws TeamConfigException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode jsonKeyTar;
        try {
            jsonKeyTar = (ObjectNode) mapper.readTree(keyString);
        } catch (IOException e) {
            throw new TeamConfigException("Error parsing KeyTar string.", e);
        }

        jsonKeyTar.fields().forEachRemaining(entry -> {
            final String keyVal = entry.getKey();
            final JsonNode jsonVal = entry.getValue();
            keyTarConfigs.add(new KeyTarConfig(keyVal,
                    jsonVal.has("profiles.base.properties.user") ? jsonVal.get("profiles.base.properties.user").asText() : null,
                    jsonVal.has("profiles.base.properties.password") ? jsonVal.get("profiles.base.properties.password").asText() : null));
        });
        return keyTarConfigs;
    }

    /**
     * Retrieve the OS credential store by querying the OS with service and account name.
     * Assign the value to keyString.
     *
     * @throws TeamConfigException error processing team configuration
     * @author Frank Giordano
     */
    @Override
    public void processKey() throws TeamConfigException {
        ValidateUtils.checkIllegalParameter(serviceName, "serviceName");
        ValidateUtils.checkIllegalParameter(accountName, "accountName");
        final Keytar instance = Keytar.getInstance();
        final String encodedString;
        try {
            encodedString = instance.getPassword(serviceName, accountName);
        } catch (KeytarException e) {
            throw new TeamConfigException("Error retrieving KeyTar password", e);
        }
        LOG.debug("KeyTar encodedString retrieved {}", encodedString);
        if (encodedString == null) {
            throw new TeamConfigException("Unknown service name or account name");
        }
        final byte[] decodedBytes = Base64.getDecoder().decode(encodedString);
        this.keyString = new String(decodedBytes);
    }

    /**
     * Set the account name.
     *
     * @param accountName string value used for OS credential store querying
     * @author Frank Giordano
     */
    @Override
    public void setAccountName(final String accountName) {
        this.accountName = accountName;
    }

    /**
     * Set the service name.
     *
     * @param serviceName string value used for OS credential store querying
     * @author Frank Giordano
     */
    @Override
    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }

}
