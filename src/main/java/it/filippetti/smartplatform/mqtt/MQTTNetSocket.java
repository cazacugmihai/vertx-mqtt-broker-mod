package it.filippetti.smartplatform.mqtt;

import io.netty.buffer.ByteBuf;
import org.dna.mqtt.moquette.proto.messages.ConnectMessage;
import org.dna.mqtt.moquette.proto.messages.PublishMessage;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.ConcurrentSharedMap;
import org.vertx.java.platform.Container;

/**
 * Created by giovanni on 07/05/2014.
 */
public class MQTTNetSocket extends MQTTSocket {

    private NetSocket netSocket;
    private ConcurrentSharedMap<String, Buffer> messagesStore;
    private ConcurrentSharedMap<String, Buffer> willMessagesStore;

    public MQTTNetSocket(Vertx vertx, Container container, NetSocket netSocket) {
        super(vertx, container);
        this.netSocket = netSocket;
        messagesStore = vertx.sharedData().getMap("messages");
        willMessagesStore = vertx.sharedData().getMap("will_messages");
    }

    public void start() {
        final MQTTTokenizer tokenizer = startTokenizer();
        netSocket.dataHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer buffer) {
                tokenizer.process(buffer.getBytes());
            }
        });
        container.logger().debug("start "+ Thread.currentThread().getName());
    }

    @Override
    protected void sendMessageToClient(Buffer bytes) {
        try {
            if (!netSocket.writeQueueFull()) {
                netSocket.write(bytes);
            } else {
                netSocket.pause();
                netSocket.drainHandler(new VoidHandler() {
                    public void handle() {
                        netSocket.resume();
                    }
                });
            }

        } catch(Throwable e) {
            container.logger().error(e.getMessage());
        }
    }

    @Override
    protected void handleConnectMessage(ConnectMessage connectMessage) {
        super.handleConnectMessage(connectMessage);
        String clientID = connectMessage.getClientID();
        // TODO: use clientID for sessions management ... ?
    }

    @Override
    protected void storeMessage(PublishMessage publishMessage) {
        try {
            ByteBuf bb = new Buffer().getByteBuf();
            encoder.encode(publishMessage, bb);
            Buffer tostore = new Buffer(bb);
            String key = publishMessage.getTopicName();
            messagesStore.put(key, tostore);
        } catch(Throwable e) {
            container.logger().error(e.getMessage());
        }
    }

    @Override
    protected void deleteMessage(PublishMessage publishMessage) {
        try {
            String key = publishMessage.getTopicName();
            if(messagesStore.containsKey(key)) {
                messagesStore.remove(key);
            }
        } catch(Throwable e) {
            container.logger().error(e.getMessage());
        }
    }

    @Override
    protected void storeWillMessage(String willMsg, byte willQos, String willTopic) {
        vertx.sharedData().getMap("willMessages").put(willTopic, willMsg);
    }
}