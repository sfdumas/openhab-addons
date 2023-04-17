/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.boschindego.internal.handler;

import static org.openhab.binding.boschindego.internal.BoschIndegoBindingConstants.*;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.boschindego.internal.IndegoController;
import org.openhab.binding.boschindego.internal.discovery.IndegoDiscoveryService;
import org.openhab.binding.boschindego.internal.exceptions.IndegoAuthenticationException;
import org.openhab.binding.boschindego.internal.exceptions.IndegoException;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BoschAccountHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jacob Laursen - Initial contribution
 */
@NonNullByDefault
public class BoschAccountHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(BoschAccountHandler.class);
    private final OAuthFactory oAuthFactory;

    private OAuthClientService oAuthClientService;
    private IndegoController controller;

    public BoschAccountHandler(Bridge bridge, HttpClient httpClient, OAuthFactory oAuthFactory) {
        super(bridge);

        this.oAuthFactory = oAuthFactory;

        oAuthClientService = oAuthFactory.createOAuthClientService(getThing().getUID().getAsString(), BSK_TOKEN_URI,
                BSK_AUTH_URI, BSK_CLIENT_ID, null, BSK_SCOPE, false);
        controller = new IndegoController(httpClient, oAuthClientService);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            try {
                AccessTokenResponse accessTokenResponse = this.oAuthClientService.getAccessTokenResponse();
                if (accessTokenResponse == null) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                            "@text/offline.conf-error.oauth2-unauthorized");
                } else {
                    updateStatus(ThingStatus.ONLINE);
                }
            } catch (OAuthException | OAuthResponseException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                        "@text/offline.conf-error.oauth2-unauthorized");
            } catch (IOException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                        "@text/offline.comm-error.oauth2-authorization-failed");
            }
        });
    }

    @Override
    public void dispose() {
        oAuthFactory.ungetOAuthService(this.getThing().getUID().getAsString());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(IndegoDiscoveryService.class);
    }

    public void authorize(String authCode) throws IndegoAuthenticationException {
        logger.info("Attempting to authorize using authorization code");

        try {
            oAuthClientService.getAccessTokenResponseByAuthorizationCode(authCode, BSK_REDIRECT_URI);
        } catch (OAuthException | OAuthResponseException | IOException e) {
            throw new IndegoAuthenticationException("Failed to authorize by authorization code " + authCode, e);
        }

        logger.info("Authorization completed successfully");

        updateStatus(ThingStatus.ONLINE);
    }

    public OAuthClientService getOAuthClientService() {
        return oAuthClientService;
    }

    public Collection<String> getSerialNumbers() throws IndegoException {
        return controller.getSerialNumbers();
    }
}
