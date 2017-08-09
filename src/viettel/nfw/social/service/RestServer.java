package viettel.nfw.social.service;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class RestServer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(RestServer.class);
    private static final String REST_PACKAGES = "viettel.nfw.social.service.rest";

    private final int port;
    private Server server;

    public RestServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            ServletHolder sh = new ServletHolder(ServletContainer.class);
            //Set the package where the services reside
            sh.setInitParameter(ServerProperties.PROVIDER_PACKAGES, REST_PACKAGES);

            server = new Server(port);
            ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
            context.addServlet(sh, "/*");
            server.start();
            server.join();
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

}
