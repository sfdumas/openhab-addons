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
 **/

package org.openhab.binding.denonmarantz.internal;

/**
 * TODO: FILL OUT
 *
 * @author Stephen Dumas - Initial Creation
 *
 */
public enum DenonMarantzModel {

    SR8012("SR-8012", 3);

    private final String name;

    private final int zoneCount;

    DenonMarantzModel(String name, int zoneCount) {
        this.name = name;
        this.zoneCount = zoneCount;
    }

    public String getName() {
        return name;
    }

    public int getZoneCount() {
        return zoneCount;
    }
}
