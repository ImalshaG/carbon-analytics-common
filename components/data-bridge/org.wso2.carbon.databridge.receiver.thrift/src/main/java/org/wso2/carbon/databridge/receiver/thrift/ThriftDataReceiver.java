/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.databridge.receiver.thrift;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.wso2.carbon.databridge.commons.exception.TransportException;
import org.wso2.carbon.databridge.commons.thrift.service.general.ThriftEventTransmissionService;
import org.wso2.carbon.databridge.commons.thrift.service.secure.ThriftSecureEventTransmissionService;
import org.wso2.carbon.databridge.commons.thrift.utils.CommonThriftConstants;
import org.wso2.carbon.databridge.core.DataBridgeReceiverService;
import org.wso2.carbon.databridge.core.exception.DataBridgeException;
import org.wso2.carbon.databridge.core.internal.utils.DataBridgeConstants;
import org.wso2.carbon.databridge.receiver.thrift.conf.ThriftDataReceiverConfiguration;
import org.wso2.carbon.databridge.receiver.thrift.internal.utils.ThriftDataReceiverConstants;
import org.wso2.carbon.databridge.receiver.thrift.service.ThriftEventTransmissionServiceImpl;
import org.wso2.carbon.databridge.receiver.thrift.service.ThriftSecureEventTransmissionServiceImpl;
import org.wso2.carbon.utils.Utils;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.net.ssl.SSLServerSocket;

/**
 * Carbon based implementation of the agent server.
 */
public class ThriftDataReceiver {
    private static final Logger log = LogManager.getLogger(ThriftDataReceiver.class);
    private DataBridgeReceiverService dataBridgeReceiverService;
    private ThriftDataReceiverConfiguration thriftDataReceiverConfiguration;
    private TServer authenticationServer;
    private TServer dataReceiverServer;

    /**
     * Initialize Carbon Agent Server.
     *
     * @param secureReceiverPort
     * @param receiverPort
     * @param dataBridgeReceiverService
     */
    public ThriftDataReceiver(int secureReceiverPort, int receiverPort,
                              DataBridgeReceiverService dataBridgeReceiverService) {
        this.dataBridgeReceiverService = dataBridgeReceiverService;
        this.thriftDataReceiverConfiguration = new ThriftDataReceiverConfiguration(secureReceiverPort, receiverPort);
    }

    /**
     * Initialize Carbon Agent Server.
     *
     * @param receiverPort
     * @param dataBridgeReceiverService
     */
    public ThriftDataReceiver(int receiverPort,
                              DataBridgeReceiverService dataBridgeReceiverService) {
        this.dataBridgeReceiverService = dataBridgeReceiverService;
        this.thriftDataReceiverConfiguration = new ThriftDataReceiverConfiguration(receiverPort +
                CommonThriftConstants.SECURE_EVENT_RECEIVER_PORT_OFFSET, receiverPort);
    }

    /**
     * Initialize Carbon Agent Server.
     *
     * @param thriftDataReceiverConfiguration
     * @param dataBridgeReceiverService
     */
    public ThriftDataReceiver(ThriftDataReceiverConfiguration thriftDataReceiverConfiguration,
                              DataBridgeReceiverService dataBridgeReceiverService) {
        this.dataBridgeReceiverService = dataBridgeReceiverService;
        this.thriftDataReceiverConfiguration = thriftDataReceiverConfiguration;
    }

    /**
     * To start the Agent server.
     *
     * @throws org.wso2.carbon.databridge.core.exception.DataBridgeException if the agent server cannot be started
     */
    public void start(String hostName)
            throws DataBridgeException {
        startSecureEventTransmission(hostName, thriftDataReceiverConfiguration.getSecureDataReceiverPort(),
                thriftDataReceiverConfiguration.getSslProtocols(), thriftDataReceiverConfiguration.getCiphers(),
                dataBridgeReceiverService);
        startEventTransmission(hostName, thriftDataReceiverConfiguration.getDataReceiverPort(),
                dataBridgeReceiverService);
    }


    private void startSecureEventTransmission(String hostName, int port, String sslProtocols, String ciphers,
                                              DataBridgeReceiverService dataBridgeReceiverService)
            throws DataBridgeException {
        try {
            String keyStore = dataBridgeReceiverService.getInitialConfig().getKeyStoreLocation();
            if (keyStore == null) {
                keyStore = System.getProperty("Security.KeyStore.Location");
                if (keyStore == null) {
                    String defaultKeyStore = Utils.getCarbonHome() + File.separator + "resources" + File.separator +
                            "security" + File.separator + "wso2carbon.jks";
                    Path defaultKeyStoreFilePath = Paths.get(defaultKeyStore);
                    if (Files.exists(defaultKeyStoreFilePath)) {
                        keyStore = defaultKeyStore;
                    } else {
                        throw new DataBridgeException("Cannot start binary agent server, " +
                                " Security.KeyStore.Location is null");
                    }
                }
            }

            String keyStorePassword = dataBridgeReceiverService.getInitialConfig().getKeyStorePassword();
            if (keyStorePassword == null) {
                keyStorePassword = System.getProperty("Security.KeyStore.Password");
                if (keyStorePassword == null) {
                    throw new DataBridgeException("Cannot start thrift agent server, not valid" +
                            " Security.KeyStore.Password is null ");
                }
            }

            startSecureEventTransmission(hostName, port, sslProtocols, ciphers, keyStore, keyStorePassword,
                    dataBridgeReceiverService);
        } catch (TransportException e) {
            throw new DataBridgeException("Cannot start agent server on port " + port, e);
        } catch (UnknownHostException e) {
            //ignore since localhost
        }
    }

    protected void startSecureEventTransmission(String hostName, int port, String sslProtocols, String ciphers,
                                                String keyStore, String keyStorePassword,
                                                DataBridgeReceiverService dataBridgeReceiverService)
            throws TransportException, UnknownHostException {
        TSSLTransportFactory.TSSLTransportParameters params =
                new TSSLTransportFactory.TSSLTransportParameters();
        params.setKeyStore(keyStore, keyStorePassword);

        TServerSocket serverTransport;
        try {
            InetAddress inetAddress = InetAddress.getByName(hostName);
            serverTransport = TSSLTransportFactory.getServerSocket(
                    port, DataBridgeConstants.CLIENT_TIMEOUT_MS, inetAddress, params);
            SSLServerSocket sslServerSocket = (javax.net.ssl.SSLServerSocket) serverTransport.getServerSocket();
            if (sslProtocols != null && sslProtocols.length() != 0) {
                String[] sslProtocolsArray = sslProtocols.split(",");
                sslServerSocket.setEnabledProtocols(sslProtocolsArray);
            }

            if (ciphers != null && ciphers.length() != 0) {
                String[] ciphersArray = ciphers.split(",");
                sslServerSocket.setEnabledCipherSuites(ciphersArray);
            }

            log.info("Thrift Server started at " + hostName);
        } catch (TTransportException e) {
            throw new TransportException("Thrift transport exception occurred ", e);
        }

        ThriftSecureEventTransmissionService.Processor<ThriftSecureEventTransmissionServiceImpl> processor =
                new ThriftSecureEventTransmissionService.Processor<ThriftSecureEventTransmissionServiceImpl>(
                        new ThriftSecureEventTransmissionServiceImpl(dataBridgeReceiverService));
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport).processor(processor)
                .maxWorkerThreads(thriftDataReceiverConfiguration.getSslMaxWorkerThreads());
        if (thriftDataReceiverConfiguration.getSslMinWorkerThreads() != ThriftDataReceiverConstants.UNDEFINED) {
            args.minWorkerThreads = thriftDataReceiverConfiguration.getSslMinWorkerThreads();
        }
        if (thriftDataReceiverConfiguration.getSslRequestTimeout() != ThriftDataReceiverConstants.UNDEFINED) {
            args.requestTimeout = thriftDataReceiverConfiguration.getSslRequestTimeout();
        }
        if (thriftDataReceiverConfiguration.getSslStopTimeoutVal() != ThriftDataReceiverConstants.UNDEFINED) {
            args.stopTimeoutVal = thriftDataReceiverConfiguration.getSslStopTimeoutVal();
        }
        authenticationServer = new TThreadPoolServer(args);
        Thread thread = new Thread(new ServerThread(authenticationServer));
        log.info("Thrift SSL port : " + port);
        thread.start();
    }

    protected void startEventTransmission(String hostName, int port,
                                          DataBridgeReceiverService dataBridgeReceiverService)
            throws DataBridgeException {
        try {
            TServerSocket serverTransport = new TServerSocket(
                    new InetSocketAddress(hostName, port));
            ThriftEventTransmissionService.Processor<ThriftEventTransmissionServiceImpl> processor =
                    new ThriftEventTransmissionService.Processor<ThriftEventTransmissionServiceImpl>(
                            new ThriftEventTransmissionServiceImpl(dataBridgeReceiverService));
            TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport).processor(processor)
                    .maxWorkerThreads(thriftDataReceiverConfiguration.getTcpMaxWorkerThreads());
            if (thriftDataReceiverConfiguration.getTcpMinWorkerThreads() != ThriftDataReceiverConstants.UNDEFINED) {
                args.minWorkerThreads = thriftDataReceiverConfiguration.getTcpMinWorkerThreads();
            }
            if (thriftDataReceiverConfiguration.getTcpRequestTimeout() != ThriftDataReceiverConstants.UNDEFINED) {
                args.requestTimeout = thriftDataReceiverConfiguration.getTcpRequestTimeout();
            }
            if (thriftDataReceiverConfiguration.getTcpStopTimeoutVal() != ThriftDataReceiverConstants.UNDEFINED) {
                args.stopTimeoutVal = thriftDataReceiverConfiguration.getTcpStopTimeoutVal();
            }
            dataReceiverServer = new TThreadPoolServer(args);
            Thread thread = new Thread(new ServerThread(dataReceiverServer));
            log.info("Thrift port : " + port);
            thread.start();
        } catch (TTransportException e) {
            throw new DataBridgeException("Cannot start Thrift server on port " + port +
                    " on host " + hostName, e);
        }
    }

    /**
     * To stop the server.
     */
    public void stop() {
        authenticationServer.stop();
        dataReceiverServer.stop();
    }

    static class ServerThread implements Runnable {
        private TServer server;

        ServerThread(TServer server) {
            this.server = server;
        }

        public void run() {
            this.server.serve();
        }
    }
}

