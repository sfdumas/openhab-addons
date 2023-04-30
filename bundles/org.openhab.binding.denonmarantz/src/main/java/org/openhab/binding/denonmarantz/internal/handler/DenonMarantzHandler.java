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
package org.openhab.binding.denonmarantz.internal.handler;

import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_COMMAND;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_INPUT;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ITEM_TYPES;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_MAIN_VOLUME;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_MAIN_VOLUME_DB;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_MAIN_ZONE_POWER;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_MUTE;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_POWER;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_SURROUND_PROGRAM;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE2_INPUT;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE2_MUTE;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE2_POWER;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE2_VOLUME;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE2_VOLUME_DB;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE3_INPUT;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE3_MUTE;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE3_POWER;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE3_VOLUME;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE3_VOLUME_DB;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE4_INPUT;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE4_MUTE;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE4_POWER;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE4_VOLUME;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.CHANNEL_ZONE4_VOLUME_DB;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.ZONE_CHANNEL_TYPES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.denonmarantz.internal.DenonMarantzStateChangedListener;
import org.openhab.binding.denonmarantz.internal.UnsupportedCommandTypeException;
import org.openhab.binding.denonmarantz.internal.config.DenonMarantzConfiguration;
import org.openhab.binding.denonmarantz.internal.connector.DenonMarantzConnector;
import org.openhab.binding.denonmarantz.internal.connector.DenonMarantzConnectorFactory;
import org.openhab.binding.denonmarantz.internal.connector.http.DenonMarantzHttpConnector;
import org.openhab.binding.denonmarantz.internal.state.DenonMarantzState;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DenonMarantzHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan-Willem Veldhuis - Initial contribution
 */
public class DenonMarantzHandler extends BaseThingHandler implements DenonMarantzStateChangedListener {

    private final Logger logger = LoggerFactory.getLogger(DenonMarantzHandler.class);

    private static final Pattern ZONE_NUMBER_PATTERN = Pattern.compile("^zone([2-4])#.*");
    private static final int RETRY_TIME_SECONDS = 30;
    private HttpClient httpClient;
    private DenonMarantzConnector connector;
    private DenonMarantzConfiguration config;
    private DenonMarantzConnectorFactory connectorFactory = new DenonMarantzConnectorFactory();
    private DenonMarantzState denonMarantzState;
    private ScheduledFuture<?> retryJob;

    public DenonMarantzHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (connector == null) {
            return;
        }

        if (connector instanceof DenonMarantzHttpConnector && command instanceof RefreshType) {
            // Refreshing individual channels isn't supported by the Http connector.
            // The connector refreshes all channels together at the configured polling interval.
            return;
        }

        try {
            switch (channelUID.getId()) {
                case CHANNEL_POWER:
                    connector.sendPowerCommand(command, 0);
                    break;
                case CHANNEL_MAIN_ZONE_POWER:
                    connector.sendPowerCommand(command, 1);
                    break;
                case CHANNEL_MUTE:
                    connector.sendMuteCommand(command, 1);
                    break;
                case CHANNEL_MAIN_VOLUME:
                    connector.sendVolumeCommand(command, 1);
                    break;
                case CHANNEL_MAIN_VOLUME_DB:
                    connector.sendVolumeDbCommand(command, 1);
                    break;
                case CHANNEL_INPUT:
                    connector.sendInputCommand(command, 1);
                    break;
                case CHANNEL_SURROUND_PROGRAM:
                    connector.sendSurroundProgramCommand(command);
                    break;
                case CHANNEL_COMMAND:
                    connector.sendCustomCommand(command);
                    break;

                case CHANNEL_ZONE2_POWER:
                    connector.sendPowerCommand(command, 2);
                    break;
                case CHANNEL_ZONE2_MUTE:
                    connector.sendMuteCommand(command, 2);
                    break;
                case CHANNEL_ZONE2_VOLUME:
                    connector.sendVolumeCommand(command, 2);
                    break;
                case CHANNEL_ZONE2_VOLUME_DB:
                    connector.sendVolumeDbCommand(command, 2);
                    break;
                case CHANNEL_ZONE2_INPUT:
                    connector.sendInputCommand(command, 2);
                    break;

                case CHANNEL_ZONE3_POWER:
                    connector.sendPowerCommand(command, 3);
                    break;
                case CHANNEL_ZONE3_MUTE:
                    connector.sendMuteCommand(command, 3);
                    break;
                case CHANNEL_ZONE3_VOLUME:
                    connector.sendVolumeCommand(command, 3);
                    break;
                case CHANNEL_ZONE3_VOLUME_DB:
                    connector.sendVolumeDbCommand(command, 3);
                    break;
                case CHANNEL_ZONE3_INPUT:
                    connector.sendInputCommand(command, 3);
                    break;

                case CHANNEL_ZONE4_POWER:
                    connector.sendPowerCommand(command, 4);
                    break;
                case CHANNEL_ZONE4_MUTE:
                    connector.sendMuteCommand(command, 4);
                    break;
                case CHANNEL_ZONE4_VOLUME:
                    connector.sendVolumeCommand(command, 4);
                    break;
                case CHANNEL_ZONE4_VOLUME_DB:
                    connector.sendVolumeDbCommand(command, 4);
                    break;
                case CHANNEL_ZONE4_INPUT:
                    connector.sendInputCommand(command, 4);
                    break;

                default:
                    throw new UnsupportedCommandTypeException();
            }
        } catch (UnsupportedCommandTypeException e) {
            logger.debug("Unsupported command {} for channel {}", command, channelUID.getId());
        }
    }

    @Override
    public void initialize() {
        cancelRetry();
        this.config = getConfigAs(DenonMarantzConfiguration.class);

        if (isConfigValid()) {
            denonMarantzState = new DenonMarantzState(this);
            configureZoneChannels();
            updateStatus(ThingStatus.UNKNOWN);
            // create connection (either Telnet or HTTP)
            // ThingStatus ONLINE/OFFLINE is set when AVR status is known.
            createConnection();
        }
    }

    private boolean isConfigValid() {
        // prevent too low values for polling interval
        if (config.httpPollingInterval < 5) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "The polling interval should be at least 5 seconds!");
            return false;
        }
        // Check zone count is within supported range
        if (config.getZoneCount() < 1 || config.getZoneCount() > 4) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "This binding supports 1 to 4 zones. Please update the zone count.");
            return false;
        }
        return true;
    }

    private void createConnection() {
        if (connector != null) {
            connector.dispose();
        }
        connector = DenonMarantzConnectorFactory.getConnector(config, denonMarantzState, scheduler, httpClient,
                this.getThing().getUID().getAsString());
        connector.connect();
    }

    private void cancelRetry() {
        ScheduledFuture<?> localRetryJob = retryJob;
        if (localRetryJob != null && !localRetryJob.isDone()) {
            localRetryJob.cancel(false);
        }
    }

    private void configureZoneChannels() {
        logger.debug("Configuring zone channels");
        Integer zoneCount = config.getZoneCount();
        List<Channel> channels = new ArrayList<>(this.getThing().getChannels());

        // construct a set with the existing channel type UIDs, to quickly check
        int currentlyConfiguredZones = channels.stream().map(channel -> channel.getUID().getId())
                .map(ZONE_NUMBER_PATTERN::matcher).filter(Matcher::find).map(matcher -> matcher.group(1))
                .mapToInt(Integer::parseInt).max().orElse(1);

        logger.debug("Currently {} Zones Configured, with {} Zones in the Configuration.", currentlyConfiguredZones,
                zoneCount);

        if (zoneCount.equals(currentlyConfiguredZones)) {
            logger.debug("No zone channel changes have been detected.");
            return;
        } else if (zoneCount > currentlyConfiguredZones) {
            channels.addAll(addChannels(zoneCount, currentlyConfiguredZones));
        } else {
            removeChannels(channels, zoneCount, currentlyConfiguredZones);

        }

        updateThing(editThing().withChannels(channels).build());
    }

    private List<Channel> addChannels(Integer zoneCount, int currentlyConfiguredZones) {
        return IntStream
                .rangeClosed(currentlyConfiguredZones + 1,
                        zoneCount)
                .boxed().peek(
                        zoneNum -> logger.debug("Adding Zone {}", zoneNum))
                .map(zoneNum -> ZONE_CHANNEL_TYPES.entrySet().stream()
                        .map(entry -> ChannelBuilder.create(
                                new ChannelUID(this.getThing().getUID(),
                                        entry.getKey().replace("?", String.valueOf(zoneNum))),
                                CHANNEL_ITEM_TYPES.get(entry.getKey())).withType(entry.getValue()).build())
                        .peek(channel -> logger.debug("Adding Channel {}", channel.getUID().getId()))
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream).toList();
    }

    private void removeChannels(List<Channel> channels, Integer zoneCount, int currentlyConfiguredZones) {
        IntStream.range(zoneCount, currentlyConfiguredZones).map(i -> currentlyConfiguredZones - i + zoneCount)
                .peek(zoneNum -> logger.debug("Removing Zone {}", zoneNum))
                .mapToObj(zoneNum -> channels.stream()
                        .filter(channel -> channel.getUID().getId().startsWith("zone" + zoneNum))
                        .collect(Collectors.toSet()))
                .flatMap(Collection::stream)
                .peek(channel -> logger.debug("Removing Channel {}", channel.getUID().getId()))
                .filter(channel -> !channels.remove(channel)).forEach(channel -> logger
                        .error("Unable to remove Channels starting with 'zone{}'", channel.getUID().getId()));
    }

    @Override
    public void dispose() {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
        cancelRetry();
        super.dispose();
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        super.channelLinked(channelUID);
        String channelID = channelUID.getId();
        if (isLinked(channelID)) {
            State state = denonMarantzState.getStateForChannelID(channelUID);
            if (state != null) {
                updateState(channelID, state);
            }
        }
    }

    @Override
    public void stateChanged(String channelID, State state) {
        logger.debug("Received state {} for channelID {}", state, channelID);

        // Don't flood the log with thing 'updated: ONLINE' each time a single channel changed
        if (this.getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
        updateState(channelID, state);
    }

    @Override
    public void connectionError(String errorMessage) {
        if (this.getThing().getStatus() != ThingStatus.OFFLINE) {
            // Don't flood the log with thing 'updated: OFFLINE' when already offline
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, errorMessage);
        }
        connector.dispose();
        retryJob = scheduler.schedule(this::createConnection, RETRY_TIME_SECONDS, TimeUnit.SECONDS);
    }
}
