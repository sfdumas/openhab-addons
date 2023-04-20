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
package org.openhab.binding.denonmarantz.internal.discovery;

import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.PARAMETER_HOST;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.THING_TYPE_AVR;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jmdns.ServiceInfo;

import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DenonMarantzDiscoveryParticipant searches for Receivers that match Denon or Marantz serial numbers.
 *
 * @author Stephen Dumas - Code Refactoring and reduction
 * @author Jan-Willem Veldhuis - Initial contribution
 *
 */
@Component
public class DenonMarantzDiscoveryParticipant implements MDNSDiscoveryParticipant {

    private Logger logger = LoggerFactory.getLogger(DenonMarantzDiscoveryParticipant.class);

    // Service type for 'Airplay enabled' receivers
    private static final String RAOP_SERVICE_TYPE = "_raop._tcp.local.";

    /**
     * Match the serial number, vendor and model of the discovered AVR.
     * Input is like "0006781D58B1@Marantz SR5008._raop._tcp.local."
     * A Denon AVR serial (MAC address) starts with 0005CD
     * A Marantz AVR serial (MAC address) starts with 000678
     */
    private static final Pattern DENON_MARANTZ_PATTERN = Pattern
            .compile("^((?:0005CD|000678)[A-Z0-9]+)@(.+)\\._raop\\._tcp\\.local\\.$");

    /**
     * Denon AVRs have a MAC address / serial number starting with 0005CD
     */
    private static final String DENON_MAC_PREFIX = "0005CD";

    /**
     * Marantz AVRs have a MAC address / serial number starting with 000678
     */
    private static final String MARANTZ_MAC_PREFIX = "000678";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(THING_TYPE_AVR);
    }

    @Override
    public String getServiceType() {
        return RAOP_SERVICE_TYPE;
    }

    @Override
    public DiscoveryResult createResult(ServiceInfo serviceInfo) {

        return Optional.ofNullable(getThingUID(serviceInfo)).map(uid -> {
            Matcher matcher = DENON_MARANTZ_PATTERN.matcher(serviceInfo.getQualifiedName());
            matcher.matches();
            return DiscoveryResultBuilder.create(uid)
                    .withLabel(buildLabel(matcher, serviceInfo.getPropertyString("am")))
                    .withProperty(PARAMETER_HOST, serviceInfo.getHostAddresses()[0])
                    .withProperty(Thing.PROPERTY_SERIAL_NUMBER, matcher.group(1).toLowerCase())
                    .withProperty(Thing.PROPERTY_VENDOR, getVendorByMacPrefix(matcher.group(1).trim()))
                    .withProperty(Thing.PROPERTY_MODEL_ID, serviceInfo.getPropertyString("am"))
                    .withRepresentationProperty(Thing.PROPERTY_SERIAL_NUMBER).build();
        }).orElse(null);
    }

    @Override
    public ThingUID getThingUID(ServiceInfo service) {
        Matcher matcher = DENON_MARANTZ_PATTERN.matcher(service.getQualifiedName());

        if (matcher.matches()) {
            logger.debug("{} seems like a supported Denon/Marantz AVR.", service.getQualifiedName());
            return new ThingUID(THING_TYPE_AVR, matcher.group(1).toLowerCase());

        } else {
            logger.trace("This discovered device {} is not supported by the DenonMarantz binding, ignoring..",
                    service.getQualifiedName());
        }
        return null;
    }

    private String getVendorByMacPrefix(String serial) {
        if (serial.startsWith(MARANTZ_MAC_PREFIX)) {
            return "Marantz";
        } else if (serial.startsWith(DENON_MAC_PREFIX)) {
            return "Denon";
        }
        return null;
    }

    private String buildLabel(Matcher matcher, String name) {
        return matcher.group(2).trim() + " (" + name + ")";
    }
}
