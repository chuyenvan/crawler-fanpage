package viettel.nfw.social.facebook.updatenews.prototype.planner;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
@ChannelHandler.Sharable
public class RemoteDownloaderMessageHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteDownloaderMessageHandler.class);
    private final AtomicLong caughtExceptions = new AtomicLong();
    private final AtomicLong acceptedRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();
    private final AtomicLong idleEvents = new AtomicLong();
    private final AtomicLong finishedRequests = new AtomicLong();
    private final AtomicLong unknownMessages = new AtomicLong();

    private final String host;
    private final int port;

    public RemoteDownloaderMessageHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static String getRemoteAddress(ChannelHandlerContext ctx) {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        caughtExceptions.incrementAndGet();
        LOG.warn("{}:{} Exception caught in IoHandler: ", new Object[]{host, port, cause});
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            LOG.warn("{}:{} Idle state", host, port);
            idleEvents.incrementAndGet();
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        LOG.debug("{}:{} Call channelRead", new Object[]{host, port});
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOG.debug("{}:{} channel inactive", host, port);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOG.debug("{}:{} channel active", host, port);
        ctx.fireChannelActive();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOG.debug("{}:{} Handler added", host, port);
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     *
     * @param ctx
     * @throws java.lang.Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LOG.debug("{}:{} Handler removed", host, port);
    }

}

