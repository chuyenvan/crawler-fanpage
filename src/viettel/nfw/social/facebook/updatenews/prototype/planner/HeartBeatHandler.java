package viettel.nfw.social.facebook.updatenews.prototype.planner;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.io.Serializable;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler should be added to both server and client pipelines before
 * handler of messages It will pass through all messages except HeartbeatMessage
 * and close channel if can't send heartbeat after idle event.
 *
 * @author duongth5
 */
public class HeartBeatHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HeartBeatHandler.class);

    private static class HeartbeatMessage implements Serializable {
    }
    private static HeartbeatMessage heartbeat = new HeartbeatMessage();

    public static ChannelPipeline addLast(ChannelPipeline pipeline) {
        final int idleDelayInSeconds = 600;
        pipeline.addLast("idleStateHandler", new IdleStateHandler(0, 0, idleDelayInSeconds))
            .addLast("heartbeatHandler", new HeartBeatHandlerImpl());
        return pipeline;
    }

    private static class HeartBeatHandlerImpl extends ChannelDuplexHandler {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                LOG.info("Idle state event. Will send heartbeat message.");
                ctx.writeAndFlush(heartbeat).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) {
                            if (future.isSuccess()) {
                                LOG.info("Heartbeat was sent successfully.");
                            } else {
                                LOG.warn("Can't send heartbeat. Close channel. Cause: ", future.cause());
                                future.channel().close();
                            }
                        }
                    }
                );
            } else {
                ctx.fireUserEventTriggered(evt);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
            if (message instanceof HeartbeatMessage) {
                LOG.info("{} heartbeat received", ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName());
            } else {
                ctx.fireChannelRead(message);
            }
        }
    }
}
