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
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.PARAMETER_TELNET_ENABLED;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.PARAMETER_ZONE_COUNT;
import static org.openhab.binding.denonmarantz.internal.DenonMarantzBindingConstants.THING_TYPE_AVR;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jmdns.ServiceInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * DenonMarantzDiscoveryParticipant searches for Receivers that match Denon or Marantz serial numbers.
 *
 * @author Stephen Dumas - Code Refactoring and moved configuration from handler to here
 * @author Jan-Willem Veldhuis - Initial contribution
 *
 */
@Component
@NonNullByDefault
public class DenonMarantzDiscoveryParticipant implements MDNSDiscoveryParticipant {

    private final Logger logger = LoggerFactory.getLogger(DenonMarantzDiscoveryParticipant.class);

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

    private final HttpClient httpClient;

    @Activate
    public DenonMarantzDiscoveryParticipant(@Reference final HttpClientFactory httpClientFactory) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(THING_TYPE_AVR);
    }

    @Override
    public String getServiceType() {
        return RAOP_SERVICE_TYPE;
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo serviceInfo) {

        if (serviceInfo.getHostAddresses().length == 0) {
            logger.debug("Could not determine IP address for the Denon/Marantz AVR");
            return null;
        }

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
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
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
        return "UNKNOWN";
    }

    private String buildLabel(Matcher matcher, String name) {
        return matcher.group(2).trim() + " (" + name + ")";
    }

    /**
     * Try to autoconfigure the connection type (Telnet or HTTP) for unmanaged Things.
     */
    private Map<String, Object> generateDefaultProperties(String host) {

        logger.debug("Trying to auto-detect the connection.");
        ContentResponse contentResponse;

        Map<String, Object> properties = new HashMap<>();
        properties.put(PARAMETER_HOST, host);
        properties.put(PARAMETER_TELNET_ENABLED, true);
        properties.put(PARAMETER_ZONE_COUNT, 2);

        getApiPort(host).ifPresent(port -> {
            if (port == 80) {
                properties.put(PARAMETER_TELNET_ENABLED, false);
            }

            properties.put(PARAMETER_ZONE_COUNT,
                    getResponse(host, port).filter(response -> response.getStatus() == HttpURLConnection.HTTP_OK)
                            .map(this::determineZoneCount).orElse(2));
        });

        return properties;
    }

    private Integer determineZoneCount(ContentResponse response) {

        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setXIncludeAware(false);
        domFactory.setExpandEntityReferences(false);

        DocumentBuilder builder;
        Node node = null;

        int zoneCount = 2;

        try {
            // see https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html
            domFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            domFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            domFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            builder = domFactory.newDocumentBuilder();
            Document dDoc = builder.parse(new InputSource(new StringReader(response.getContentAsString())));
            XPath xPath = XPathFactory.newInstance().newXPath();
            node = (Node) xPath.evaluate("/Device_Info/DeviceZones/text()", dDoc, XPathConstants.NODE);

        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException
                | NumberFormatException e) {
            logger.debug("Something went wrong with looking up the zone count in Deviceinfo.xml: {}", e.getMessage());
        }

        if (node != null) {
            logger.debug("Discovered number of zones: {}", Integer.parseInt(node.getNodeValue()));
            zoneCount = Integer.parseInt(node.getNodeValue());
        }

        return zoneCount;
    }

    private Optional<Integer> getApiPort(String host) {

        if (testConnectionToHost(host, 80)) {
            logger.debug("We can access the HTTP API, disabling the Telnet mode by default.");
            return Optional.of(80);
        } else if (testConnectionToHost(host, 8080)) {
            logger.debug("This model responds to HTTP port 8080, we use this port to retrieve the number of zones.");
            return Optional.of(8080);
        }

        return Optional.empty();
    }

    private Optional<ContentResponse> getResponse(String host, int port) {
        try {
            return Optional.of(httpClient.newRequest("http://" + host + ":" + port + "/goform/Deviceinfo.xml")
                    .timeout(3, TimeUnit.SECONDS).send());
        } catch (Exception e) {
            logger.debug("Error connecting to {} on port {}", host, port);
            return Optional.empty();
        }
    }

    private boolean testConnectionToHost(String host, int port) {
        return getResponse(host, port).map(response -> response.getStatus() == HttpURLConnection.HTTP_OK).orElse(false);
    }
}
