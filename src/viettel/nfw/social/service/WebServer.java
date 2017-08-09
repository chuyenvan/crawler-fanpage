package viettel.nfw.social.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.evaluation.RunEvaluation;

/**
 *
 * @author duongth5
 */
public class WebServer implements Runnable {

    private final int port;
    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);

    public WebServer(int port) {
        this.port = port;
    }

    private Server jetty;

    @Override
    public void run() {

        jetty = new Server(port);

        ContextHandler activeAccountHandler = new ContextHandler();
        activeAccountHandler.setContextPath("/active");
        activeAccountHandler.setHandler(new ActiveAccountHandler());

        ContextHandler evaluateProfileUrlHandler = new ContextHandler();
        evaluateProfileUrlHandler.setContextPath("/eval");
        evaluateProfileUrlHandler.setHandler(new EvaluateProfileUrlHandler());

        HandlerCollection collection = new HandlerCollection();
        collection.addHandler(activeAccountHandler);
        collection.addHandler(evaluateProfileUrlHandler);

        jetty.setHandler(collection);

        try {
            jetty.start();
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class ActiveAccountHandler extends AbstractHandler {

        @Override
        public void handle(String string, Request rqst, HttpServletRequest hsr, HttpServletResponse hsr1) throws IOException, ServletException {

            rqst.setHandled(true);
            String paramValue = rqst.getParameter("account");
            String mess = "default";
            if (StringUtils.isEmpty(paramValue)) {
                mess = "empty account";
            } else {
//                Account activeAccount = Run.crawlAccounts.get(paramValue);
//                AccountStatus status = activeAccount.getStatus();
//                if (status.equals(AccountStatus.ACTIVE)) {
//                    mess = "account is crawling " + paramValue;
//                } else {
//                    activeAccount.setStatus(AccountStatus.ACTIVE);
//                    Run.waitingAccounts.add(paramValue);
//                    mess = "activated account " + paramValue;
//                }
            }
            hsr1.setStatus(HttpServletResponse.SC_OK);
            hsr1.setContentType("text/html");
            hsr1.setCharacterEncoding("UTF-8");
            hsr1.getOutputStream().write((mess).getBytes());
            hsr1.getOutputStream().close();
        }
    }

    private static class EvaluateProfileUrlHandler extends AbstractHandler {

        @Override
        public void handle(String string, Request rqst, HttpServletRequest hsr, HttpServletResponse hsr1) throws IOException, ServletException {
            rqst.setHandled(true);
            String paramValue = rqst.getParameter("url");
            String mess;
            if (StringUtils.isEmpty(paramValue)) {
                mess = "Empty Url!!!";
            } else {
                LOG.info("Receive URL: {}", paramValue);
                try {
                    URI uri = new URI(paramValue);
                    RunEvaluation.urlQueue.add(uri.toString());
                    mess = "Added";
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                    mess = "URL error";
                }
            }
            hsr1.setStatus(HttpServletResponse.SC_OK);
            hsr1.setContentType("text/html");
            hsr1.setCharacterEncoding("UTF-8");
            hsr1.getOutputStream().write((mess).getBytes());
            hsr1.getOutputStream().close();
        }
    }

}
