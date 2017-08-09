package viettel.nfw.social.facebook.updatenews.prototype.planner;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class PersistentRemoteDownloader implements RemoteDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentRemoteDownloader.class);
    private static final int CONNECTION_FAULTS_BETWEEN_RESOLVES = 10;
    private static final int MAX_RECEIVED_MESSAGE_SIZE = 40 * 1024 * 1024;

    private final String host;
    private final int port;
    private final Bootstrap bootstrap;
    private final RemoteDownloaderMessageHandler downloaderHandler;
    private final AtomicLong openedSessions = new AtomicLong();
    private final AtomicLong closedSessions = new AtomicLong();

    private InetAddress networkAddress = null;
    private long countOfConsecutiveConnectionFaults = 0;

    private volatile long connectionFails = 0L;
    private volatile Channel channel = null;

    public PersistentRemoteDownloader(
        final String host, final int port,
        NioEventLoopGroup eventGroup) {
        this.host = host;
        this.port = port;
        this.downloaderHandler = new RemoteDownloaderMessageHandler(host, port);

        this.bootstrap = new Bootstrap();
        this.bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000);
        this.bootstrap.group(eventGroup)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer() {

                @Override
                protected void initChannel(Channel ch) throws Exception {
                    try {
                        ch.pipeline().addLast(
                            new ObjectEncoder(),
                            new ObjectDecoder(MAX_RECEIVED_MESSAGE_SIZE, ClassResolvers.softCachingResolver(null)));
                        HeartBeatHandler.addLast(ch.pipeline())
                        .addLast(downloaderHandler);
                    } catch (Exception ex) {
                        LOG.error("{}:{} Can't init channel", host, port);
                        throw ex;
                    }
                }
            });
    }

    @Override
    public boolean isAlive() {
        LOG.trace("{}:{} Enter isAlive", host, port);
        return channel != null;
    }

    @Override
    public boolean sendRequest(String value) {
        LOG.debug("{}:{} Enter sendRequest", host, port);
        return false;
    }

    @Override
    public boolean check() {
        LOG.trace("{}:{} Enter check", host, port);

        if (!isAlive()) {
            LOG.info("{}:{} Start reconnect since no_channel", host, port);
            disconnect();
            if (!connect()) {
                ++connectionFails;
                return false;
            }
        }

        return true;
    }

    private synchronized boolean connect() {
        LOG.debug("{}:{} Enter connect", new Object[]{host, port});
        if (networkAddress == null || countOfConsecutiveConnectionFaults >= CONNECTION_FAULTS_BETWEEN_RESOLVES) {
            try {
                networkAddress = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                networkAddress = null;
            }
            if (networkAddress != null) {
                countOfConsecutiveConnectionFaults = 0;
            }
        }
        if (networkAddress == null) {
            return false;
        }

        boolean res = false;
        try {
            ChannelFuture future = bootstrap.connect(host, port).sync();
            if (!future.isDone()) {
                future.cancel(true);
                ++countOfConsecutiveConnectionFaults;
                LOG.error("Connection fault after timeout for {}:{}", new Object[]{networkAddress, port});
            } else if (future.isSuccess()) {
                channel = future.channel();
                channel.closeFuture().addListener(
                    new ChannelFutureListener() {

                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                closedSessions.incrementAndGet();
                                LOG.info("{}:{} Channel closed", host, port);
                            } else {
                                LOG.error("{}:{} Channel wasn't closed successfully: ", future.cause());
                                channel.close().sync();
                            }
                            channel.pipeline().remove(downloaderHandler);
                            channel = null;
                        }
                    }
                );
                countOfConsecutiveConnectionFaults = 0;
                LOG.info("Connection established for {}:{} ", new Object[]{networkAddress, port});
                openedSessions.incrementAndGet();
                res = true;
            } else {
                future.cancel(true);
                //noinspection ThrowableResultOfMethodCallIgnored
                LOG.error(
                    "Failed connection to {}:{} cause {}",
                    new Object[]{networkAddress, port, future.cause()}
                );
            }
        } catch (InterruptedException e) {
            LOG.warn("{}:{} Connection was interrupted", host, port);
        } catch (Exception e) {
            LOG.error("{}:{} Exception when connect {}", new Object[]{host, port, e});
        }
        if (!res) {
            ++countOfConsecutiveConnectionFaults;
        }
        return res;
    }

    @Override
    public synchronized void disconnect() {
        LOG.debug("{}:{} Enter disconnect", host, port);
        if (channel != null && channel.isOpen()) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}

