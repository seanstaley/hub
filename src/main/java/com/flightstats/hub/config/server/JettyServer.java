package com.flightstats.hub.config.server;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.flightstats.hub.app.GuiceToHK2Adapter;
import com.flightstats.hub.app.HttpAndWSHandler;
import com.flightstats.hub.app.HubHost;
import com.flightstats.hub.app.ObjectMapperResolver;
import com.flightstats.hub.config.binding.HubBindings;
import com.flightstats.hub.config.properties.AppProperties;
import com.flightstats.hub.config.properties.SystemProperties;
import com.flightstats.hub.filter.CORSFilter;
import com.flightstats.hub.filter.MetricsRequestFilter;
import com.flightstats.hub.filter.StreamEncodingFilter;
import com.flightstats.hub.ws.WebSocketChannelEndpoint;
import com.flightstats.hub.ws.WebSocketDayEndpoint;
import com.flightstats.hub.ws.WebSocketHashEndpoint;
import com.flightstats.hub.ws.WebSocketHourEndpoint;
import com.flightstats.hub.ws.WebSocketMinuteEndpoint;
import com.flightstats.hub.ws.WebSocketSecondEndpoint;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.glassfish.jersey.jetty.JettyHttpContainer;
import org.glassfish.jersey.message.DeflateEncoder;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;

import javax.websocket.server.ServerContainer;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Slf4j
public class JettyServer {

    private final Injector injector;

    public JettyServer(Injector injector) {
        this.injector = injector;
    }

    public Server start() throws Exception {

        Server server = new Server();
        HttpConfiguration httpConfig = new HttpConfiguration();
        SslContextFactory sslContextFactory = getSslContextFactory();
        if (null != sslContextFactory) {
            httpConfig.addCustomizer(new SecureRequestCustomizer());
        }

        SystemProperties systemProperties = injector.getInstance(SystemProperties.class);
        ConnectionFactory connectionFactory = new HttpConnectionFactory(httpConfig);
        ServerConnector serverConnector = new ServerConnector(server, sslContextFactory, connectionFactory);
        serverConnector.setHost(systemProperties.getHttpBindIp());
        serverConnector.setPort(HubHost.getLocalPort());
        serverConnector.setIdleTimeout(systemProperties.getHttpIdleTimeInMillis());
        server.setConnectors(new Connector[]{serverConnector});

        // build Jersey HTTP context
        ResourceConfig resourceConfig = buildResourceConfig(injector);
        JettyHttpContainer httpContainer = ContainerFactory.createContainer(JettyHttpContainer.class, resourceConfig);

        // build Jetty WebSocket context
        ServletContextHandler wsContext = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
        wsContext.setContextPath("/");
        ServerContainer wsContainer = WebSocketServerContainerInitializer.configureContext(wsContext);
        wsContainer.addEndpoint(WebSocketChannelEndpoint.class);
        wsContainer.addEndpoint(WebSocketDayEndpoint.class);
        wsContainer.addEndpoint(WebSocketHourEndpoint.class);
        wsContainer.addEndpoint(WebSocketMinuteEndpoint.class);
        wsContainer.addEndpoint(WebSocketSecondEndpoint.class);
        wsContainer.addEndpoint(WebSocketHashEndpoint.class);

        // use handler collection to choose the proper context
        HttpAndWSHandler handler = new HttpAndWSHandler(injector.getInstance(MetricsRequestFilter.class));
        handler.addHttpHandler(httpContainer);
        handler.addWSHandler(wsContext);
        server.setHandler(handler);

        // start everything up
        server.start();

        return server;
    }

    private ResourceConfig buildResourceConfig(Injector injector) {
        ResourceConfig config = new ResourceConfig();
        config.register(new GuiceToHK2Adapter(injector));
        config.register(new ObjectMapperResolver(HubBindings.objectMapper()));
        config.register(JacksonJsonProvider.class);
        config.registerClasses(
                CORSFilter.class,
                EncodingFilter.class,
                StreamEncodingFilter.class,
                GZipEncoder.class,
                DeflateEncoder.class
        );
        config.packages("com.flightstats.hub");
        return config;
    }

    private SslContextFactory getSslContextFactory() throws IOException {
        AppProperties appProperties = injector.getInstance(AppProperties.class);

        SslContextFactory sslContextFactory = null;
        if (appProperties.isAppEncrypted()) {
            log.info("starting hub with ssl!");

            final String keyStorePath = appProperties.getKeyStorePath() + HubHost.getLocalName() + ".jks";
            log.info("using key store path: {}", keyStorePath);

            sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(keyStorePath);

            String keyStorePasswordPath = appProperties.getKeyStorePasswordPath();
            URL passwordUrl = new File(keyStorePasswordPath).toURI().toURL();
            String password = Resources.readLines(passwordUrl, StandardCharsets.UTF_8).get(0);
            sslContextFactory.setKeyStorePassword(password);
        }
        return sslContextFactory;
    }

}