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

package org.openhab.binding.denonmarantz.internal.state;

import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

/**
 * @author Stephen Dumas - Initial Creation
 */
public class DenonMarantzStateFactory {

    public static State getDefaultStateByName(String channelId) {
        String channelType = channelId.split("#")[1];

        if ("power".equals(channelId.split("#")[1])) {
            return OnOffType.OFF;
        }
        return null;
    }
}
