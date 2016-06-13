/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import ch.qos.logback.classic.Level;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static class Params {

        @Arg(dest = "schemas-dir")
        public File schemasDir;

        @Arg(dest = "devices-count")
        public int deviceCount;

        @Arg(dest = "devices-per-port")
        public int devicesPerPort;

        @Arg(dest = "starting-port")
        public int startingPort;

        @Arg(dest = "generate-config-connection-timeout")
        public int generateConfigsTimeout;

        @Arg(dest = "generate-config-address")
        public String generateConfigsAddress;

        @Arg(dest = "distro-folder")
        public File distroFolder;

        @Arg(dest = "generate-configs-batch-size")
        public int generateConfigBatchSize;

        @Arg(dest = "ssh")
        public boolean ssh;

        @Arg(dest = "exi")
        public boolean exi;

        @Arg(dest = "debug")
        public boolean debug;

        @Arg(dest = "notification-file")
        public File notificationFile;

        @Arg(dest = "md-sal")
        public boolean mdSal;

        @Arg(dest = "initial-config-xml-file")
        public File initialConfigXMLFile;

        static ArgumentParser getParser() {
            final ArgumentParser parser = ArgumentParsers.newArgumentParser("netconf testool");

            parser.description("Netconf device simulator. Detailed info can be found at https://wiki.opendaylight.org/view/OpenDaylight_Controller:Netconf:Testtool#Building_testtool");

            parser.addArgument("--device-count")
                    .type(Integer.class)
                    .setDefault(1)
                    .help("Number of simulated netconf devices to spin. This is the number of actual ports open for the devices.")
                    .dest("devices-count");

            parser.addArgument("--devices-per-port")
                    .type(Integer.class)
                    .setDefault(1)
                    .help("Amount of config files generated per port to spoof more devices then are actually running")
                    .dest("devices-per-port");

            parser.addArgument("--schemas-dir")
                    .type(File.class)
                    .help("Directory containing yang schemas to describe simulated devices. Some schemas e.g. netconf monitoring and inet types are included by default")
                    .dest("schemas-dir");

            parser.addArgument("--notification-file")
                    .type(File.class)
                    .help("Xml file containing notifications that should be sent to clients after create subscription is called")
                    .dest("notification-file");

            parser.addArgument("--initial-config-xml-file")
                    .type(File.class)
                    .help("Xml file containing initial simulatted configuration to be returned via get-config rpc")
                    .dest("initial-config-xml-file");

            parser.addArgument("--starting-port")
                    .type(Integer.class)
                    .setDefault(17830)
                    .help("First port for simulated device. Each other device will have previous+1 port number")
                    .dest("starting-port");

            parser.addArgument("--generate-config-connection-timeout")
                    .type(Integer.class)
                    .setDefault((int)TimeUnit.MINUTES.toMillis(30))
                    .help("Timeout to be generated in initial config files")
                    .dest("generate-config-connection-timeout");

            parser.addArgument("--generate-config-address")
                    .type(String.class)
                    .setDefault("127.0.0.1")
                    .help("Address to be placed in generated configs")
                    .dest("generate-config-address");

            parser.addArgument("--generate-configs-batch-size")
                    .type(Integer.class)
                    .setDefault(4000)
                    .help("Number of connector configs per generated file")
                    .dest("generate-configs-batch-size");

            parser.addArgument("--distribution-folder")
                    .type(File.class)
                    .help("Directory where the karaf distribution for controller is located")
                    .dest("distro-folder");

            parser.addArgument("--ssh")
                    .type(Boolean.class)
                    .setDefault(true)
                    .help("Whether to use ssh for transport or just pure tcp")
                    .dest("ssh");

            parser.addArgument("--exi")
                    .type(Boolean.class)
                    .setDefault(true)
                    .help("Whether to use exi to transport xml content")
                    .dest("exi");

            parser.addArgument("--debug")
                    .type(Boolean.class)
                    .setDefault(false)
                    .help("Whether to use debug log level instead of INFO")
                    .dest("debug");

            parser.addArgument("--md-sal")
                    .type(Boolean.class)
                    .setDefault(false)
                    .help("Whether to use md-sal datastore instead of default simulated datastore.")
                    .dest("md-sal");

            return parser;
        }

        void validate() {
            checkArgument(deviceCount > 0, "Device count has to be > 0");
            checkArgument(startingPort > 1023, "Starting port has to be > 1023");
            checkArgument(devicesPerPort > 0, "Atleast one device per port needed");

            if(schemasDir != null) {
                checkArgument(schemasDir.exists(), "Schemas dir has to exist");
                checkArgument(schemasDir.isDirectory(), "Schemas dir has to be a directory");
                checkArgument(schemasDir.canRead(), "Schemas dir has to be readable");
            }
        }

        @Override
        public String toString() {
            StringBuilder params = new StringBuilder();
            params.append("schemas-dir: " + schemasDir + "/n");
            params.append("devices-count: " + deviceCount + "/n");
            params.append("devices-per-port: " + devicesPerPort + "/n");
            params.append("starting-port: " + startingPort + "/n");
            params.append("generate-config-connection-timeout" + generateConfigsTimeout + "/n");
            params.append("distro-folder: " + distroFolder + "/n");
            params.append("generate-configs-batch-size: " + generateConfigBatchSize + "/n");
            params.append("ssh: " + ssh + "/n");
            params.append("exi: " + exi + "/n");
            params.append("debug: " + debug + "/n");
            params.append("notification-file: " + notificationFile + "/n");
            params.append("md-sal: " + mdSal + "/n");
            params.append("initial-config-xml-file: " + initialConfigXMLFile);

            return params.toString();
        }
    }

    public static void main(final String[] args) {
        final Params params = parseArgs(args, Params.getParser());
        params.validate();

        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);

        final NetconfDeviceSimulator netconfDeviceSimulator = new NetconfDeviceSimulator();
        try {
            LOG.debug("Trying to start netconf test-tool with parameters {}", params);
            final List<Integer> openDevices = netconfDeviceSimulator.start(params);
            if (openDevices.size() == 0) {
                LOG.error("Failed to start any simulated devices, exiting...");
                System.exit(1);
            }
            if(params.distroFolder != null) {
                final ConfigGenerator configGenerator = new ConfigGenerator(params.distroFolder, openDevices);
                final List<File> generated = configGenerator.generate(
                        params.ssh, params.generateConfigBatchSize,
                        params.generateConfigsTimeout, params.generateConfigsAddress,
                        params.devicesPerPort);
                configGenerator.updateFeatureFile(generated);
                configGenerator.changeLoadOrder();
            }
        } catch (final Exception e) {
            LOG.error("Unhandled exception", e);
            netconfDeviceSimulator.close();
            System.exit(1);
        }

        // Block main thread
        synchronized (netconfDeviceSimulator) {
            try {
                netconfDeviceSimulator.wait();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Params parseArgs(final String[] args, final ArgumentParser parser) {
        final Params opt = new Params();
        try {
            parser.parseArgs(args, opt);
            return opt;
        } catch (final ArgumentParserException e) {
            parser.handleError(e);
        }

        System.exit(1);
        return null;
    }

    private static class ConfigGenerator {
        public static final String NETCONF_CONNECTOR_XML = "/99-netconf-connector-simulated.xml";
        public static final String SIM_DEVICE_SUFFIX = "-sim-device";

        private static final String SIM_DEVICE_CFG_PREFIX = "simulated-devices_";
        private static final String ETC_KARAF_PATH = "etc/";
        private static final String ETC_OPENDAYLIGHT_KARAF_PATH = ETC_KARAF_PATH + "opendaylight/karaf/";

        public static final String NETCONF_CONNECTOR_ALL_FEATURE = "odl-netconf-connector-all";
        private static final String ORG_OPS4J_PAX_URL_MVN_CFG = "org.ops4j.pax.url.mvn.cfg";

        private final File configDir;
        private final List<Integer> openDevices;
        private final List<File> ncFeatureFiles;
        private final File etcDir;
        private final File loadOrderCfgFile;

        public ConfigGenerator(final File directory, final List<Integer> openDevices) {
            this.configDir = new File(directory, ETC_OPENDAYLIGHT_KARAF_PATH);
            this.etcDir = new File(directory, ETC_KARAF_PATH);
            this.loadOrderCfgFile = new File(etcDir, ORG_OPS4J_PAX_URL_MVN_CFG);
            this.ncFeatureFiles = getFeatureFile(directory, "features-netconf-connector", "xml");
            this.openDevices = openDevices;
        }

        public List<File> generate(final boolean useSsh, final int batchSize,
                                   final int generateConfigsTimeout, final String address,
                                   final int devicesPerPort) {
            if(configDir.exists() == false) {
                Preconditions.checkState(configDir.mkdirs(), "Unable to create directory " + configDir);
            }

            for (final File file : configDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(final File pathname) {
                    return !pathname.isDirectory() && pathname.getName().startsWith(SIM_DEVICE_CFG_PREFIX);
                }
            })) {
                Preconditions.checkState(file.delete(), "Unable to clean previous generated file %s", file);
            }

            try(InputStream stream = Main.class.getResourceAsStream(NETCONF_CONNECTOR_XML)) {
                checkNotNull(stream, "Cannot load %s", NETCONF_CONNECTOR_XML);
                String configBlueprint = CharStreams.toString(new InputStreamReader(stream, Charsets.UTF_8));

                final String before = configBlueprint.substring(0, configBlueprint.indexOf("<module>"));
                final String middleBlueprint = configBlueprint.substring(configBlueprint.indexOf("<module>"), configBlueprint.indexOf("</module>"));
                final String after = configBlueprint.substring(configBlueprint.indexOf("</module>") + "</module>".length());

                int connectorCount = 0;
                Integer batchStart = null;
                StringBuilder b = new StringBuilder();
                b.append(before);

                final List<File> generatedConfigs = Lists.newArrayList();

                for (final Integer openDevice : openDevices) {
                    if(batchStart == null) {
                        batchStart = openDevice;
                    }

                    for (int i = 0; i < devicesPerPort; i++) {
                        final String name = String.valueOf(openDevice) + SIM_DEVICE_SUFFIX + (i == 0 ? "" : "-" + String.valueOf(i));
                        String configContent = String.format(middleBlueprint, name, address, String.valueOf(openDevice), String.valueOf(!useSsh));
                        configContent = String.format("%s%s%d%s\n%s\n", configContent, "<connection-timeout-millis>", generateConfigsTimeout, "</connection-timeout-millis>", "</module>");

                        b.append(configContent);
                        connectorCount++;
                        if(connectorCount == batchSize) {
                            b.append(after);
                            final File to = new File(configDir, String.format(SIM_DEVICE_CFG_PREFIX + "%d-%d.xml", batchStart, openDevice));
                            generatedConfigs.add(to);
                            Files.write(b.toString(), to, Charsets.UTF_8);
                            connectorCount = 0;
                            b = new StringBuilder();
                            b.append(before);
                            batchStart = null;
                        }
                    }
                }

                // Write remaining
                if(connectorCount != 0) {
                    b.append(after);
                    final File to = new File(configDir, String.format(SIM_DEVICE_CFG_PREFIX + "%d-%d.xml", batchStart, openDevices.get(openDevices.size() - 1)));
                    generatedConfigs.add(to);
                    Files.write(b.toString(), to, Charsets.UTF_8);
                }

                LOG.info("Config files generated in {}", configDir);
                return generatedConfigs;
            } catch (final IOException e) {
                throw new RuntimeException("Unable to generate config files", e);
            }
        }


        public void updateFeatureFile(final List<File> generated) {
            // TODO karaf core contains jaxb for feature files, use that for
            // modification
            try {
                for (final File featureFile : ncFeatureFiles) {
                    final Document document = XmlUtil.readXmlToDocument(Files
                            .toString(featureFile, Charsets.UTF_8));
                    final NodeList childNodes = document.getDocumentElement().getChildNodes();

                    for (int i = 0; i < childNodes.getLength(); i++) {
                        final Node item = childNodes.item(i);
                        if (item instanceof Element == false) {
                            continue;
                        }
                        if (item.getLocalName().equals("feature") == false) {
                            continue;
                        }

                        if (NETCONF_CONNECTOR_ALL_FEATURE
                                .equals(((Element) item).getAttribute("name"))) {
                            final Element ncAllFeatureDefinition = (Element) item;
                            // Clean previous generated files
                            for (final XmlElement configfile : XmlElement
                                    .fromDomElement(ncAllFeatureDefinition)
                                    .getChildElements("configfile")) {
                                ncAllFeatureDefinition.removeChild(configfile.getDomElement());
                            }
                            for (final File file : generated) {
                                final Element configfile = document.createElement("configfile");
                                configfile.setTextContent("file:"
                                        + ETC_OPENDAYLIGHT_KARAF_PATH
                                        + file.getName());
                                configfile.setAttribute(
                                        "finalname",
                                        ETC_OPENDAYLIGHT_KARAF_PATH
                                                + file.getName());
                                ncAllFeatureDefinition.appendChild(configfile);
                            }
                        }
                    }

                    Files.write(XmlUtil.toString(document), featureFile,Charsets.UTF_8);
                    LOG.info("Feature file {} updated", featureFile);
                }
            } catch (final IOException e) {
                throw new RuntimeException("Unable to load features file as a resource");
            } catch (final SAXException e) {
                throw new RuntimeException("Unable to parse features file");
            }
        }


        private static List<File> getFeatureFile(final File distroFolder, final String featureName, final String suffix) {
            checkExistingDir(distroFolder, String.format("Folder %s does not exist", distroFolder));

            final File systemDir = checkExistingDir(new File(distroFolder, "system"), String.format("Folder %s does not contain a karaf distro, folder system is missing", distroFolder));

            //check if beryllium path exists, if it doesnt check for lithium and fail/succeed after
            File netconfConnectorFeaturesParentDir = new File(systemDir, "org/opendaylight/netconf/" + featureName);
            if (!netconfConnectorFeaturesParentDir.exists() || !netconfConnectorFeaturesParentDir.isDirectory()) {
                netconfConnectorFeaturesParentDir = checkExistingDir(new File(systemDir, "org/opendaylight/controller/" + featureName), String.format("Karaf distro in %s does not contain netconf-connector features", distroFolder));
            }

            // Find newest version for features
            final File newestVersionDir = Collections.max(
                    Lists.newArrayList(netconfConnectorFeaturesParentDir.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(final File pathname) {
                            return pathname.isDirectory();
                        }
                    })), new Comparator<File>() {
                        @Override
                        public int compare(final File o1, final File o2) {
                            return o1.getName().compareTo(o2.getName());
                        }
                    });

            return Lists.newArrayList(newestVersionDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(final File pathname) {
                    return pathname.getName().contains(featureName)
                            && Files.getFileExtension(pathname.getName()).equals(suffix);
                }
            }));
        }

        private static File checkExistingDir(final File folder, final String msg) {
            Preconditions.checkArgument(folder.exists(), msg);
            Preconditions.checkArgument(folder.isDirectory(), msg);
            return folder;
        }

        public void changeLoadOrder() {
            try {
                Files.write(ByteStreams.toByteArray(getClass().getResourceAsStream("/" +ORG_OPS4J_PAX_URL_MVN_CFG)), loadOrderCfgFile);
                LOG.info("Load order changed to prefer local bundles/features by rewriting file {}", loadOrderCfgFile);
            } catch (IOException e) {
                throw new RuntimeException("Unable to rewrite features file " + loadOrderCfgFile, e);
            }
        }
    }
}
