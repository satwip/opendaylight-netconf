/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;

@Command(name = "netconf:disconnect-device", scope = "netconf", description = "Disconnect netconf device.")
public class NetconfDisconnectDeviceCommand extends AbstractAction {

    protected final NetconfConsoleProvider service;

    public NetconfDisconnectDeviceCommand(final NetconfConsoleProvider service) {
        this.service = service;
    }

    @Option(name = "-i",
            aliases = { "--ipaddress" },
            description = "IP address of the netconf device",
            required = true,
            multiValued = false)
    private String deviceIp = "";

    @Option(name = "-p",
            aliases = { "--port" },
            description = "Port of the netconf device",
            required = true,
            multiValued = false)
    private String devicePort;

    @Override
    protected Object doExecute() throws Exception {
        if (!NetconfCommandUtils.isIpValid(deviceIp) && !NetconfCommandUtils.isPortValid(devicePort)) {
            return "Invalid IP:" + deviceIp + " or Port:" + devicePort + "Please enter a valid entry to proceed.";
        }

        boolean status = service.disconnectDevice(deviceIp, devicePort);
        String message = status ? "Netconf connector disconnected succesfully"
                : "Failed to disconnect netconf connector. Refer to karaf.log for details.";
        return message;
    }
}
