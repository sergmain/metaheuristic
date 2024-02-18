package ai.metaheuristic.ai.processor.ws;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.tomcat.websocket.Constants;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Sergio Lissner
 * Date: 2/6/2024
 * Time: 12:07 PM
 */
@Slf4j
public class ProcessorWebsocketService {

    public static class MyStompFrameHandler implements StompFrameHandler  {

        private final String url;
        private final Consumer<String> eventConsumerFunc;

        public MyStompFrameHandler(String url, Consumer<String> eventConsumerFunc) {
            this.url = url;
            this.eventConsumerFunc = eventConsumerFunc;
        }

        @Override
        @NonNull
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, @Nullable Object payload) {
            log.debug(url + ", payload: " + payload);
            String text = (String)payload;
            eventConsumerFunc.accept(text);
        }
    }

    public static class WebSocketInfra {
        private final StandardWebSocketClient webSocketClient;
        private final WebSocketStompClient stompClient;
        private final String url;
        private final MyStompSessionHandler sessionHandler;
        private final AtomicBoolean inProcess = new AtomicBoolean();
        @Nullable
        private Thread wsThread = null;
        @Nullable
        private Thread mainThread = null;
        private final Consumer<String> eventConsumerFunc;

        public WebSocketInfra(String url, String  user, String pass, Consumer<String> eventConsumerFunc) {
            this.url = url;
            this.eventConsumerFunc = eventConsumerFunc;
            webSocketClient = new StandardWebSocketClient();
            // hard-lock on Apache Tomcat
            webSocketClient.setUserProperties(Map.of(
                Constants.WS_AUTHENTICATION_USER_NAME, user,
                Constants.WS_AUTHENTICATION_PASSWORD, pass
            ));
            stompClient = new WebSocketStompClient(webSocketClient);
            stompClient.setMessageConverter(new StringMessageConverter());
            final SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();
            stompClient.setTaskScheduler(taskScheduler); // for heartbeats

            sessionHandler = new MyStompSessionHandler(url, url1 -> connectToServer(), inProcess::get, this::terminateWsThread);
        }

        public void terminateWsThread() {
            if (wsThread!=null) {
                System.out.println("Start terminating ws thread, " + url);
                wsThread.interrupt();
                wsThread = null;
                System.out.println("\tws thread was terminated, " + url);
            }
        }

        public void terminateMainThread() {
            if (mainThread!=null) {
                System.out.println("Start terminating mian thread, " + url);
                mainThread.interrupt();
                mainThread = null;
                System.out.println("\tmain thread was terminated, " + url);
            }
        }

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        private boolean end = false;

        private void runInfra() {
            mainThread = Thread.startVirtualThread(this::runInfraLoop);
        }

        private void runInfraLoop() {
            while (!end) {
                if (wsThread==null) {
                    writeLock.lock();
                    try {
                        if (wsThread==null) {
                            System.out.println("Create a new thread for connecting to server, " + url);
                            wsThread = Thread.startVirtualThread(this::connectToServer);
                        }
                    }
                    finally {
                        writeLock.unlock();
                    }
                }
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                    //
                }
            }
        }

        public void destroy() {
            end = true;
            terminateMainThread();
            terminateWsThread();
        }

        private boolean connectToServer()  {
            System.out.println("start processing CompletableFuture, " + url);
            inProcess.set(true);
            try {
                CompletableFuture<StompSession> future = stompClient.connectAsync(url, sessionHandler);

                System.out.println("\twaiting for completion, " + url);
                StompSession session = future.get();
                if (session!=null) {
                    StompHeaders headers = new StompHeaders();
                    headers.add("url", url);
                    headers.setDestination("/topic/events");
                    session.subscribe(headers, new MyStompFrameHandler(url, eventConsumerFunc));
                    sessionHandler.initialized = true;
                    System.out.println("\tinitialization of session was completed, " + url);
                }
                else {
                    System.out.println("\tsession is null, " + url);
                }
                return true;
            }
            catch (Throwable e) {
                if (!"IOException: The remote computer refused the network connection".equals(ExceptionUtils.getRootCauseMessage(e))) {
                    log.error("Error, " + url, e);
                }
            }
            finally {
                inProcess.set(false);
            }
            return false;
        }
    }

    public static class MyStompSessionHandler extends StompSessionHandlerAdapter {

        private final String url;
        private final Function<String, Boolean> connectToServerFunc;
        private final Supplier<Boolean> statusFunc;
        private final Runnable terminateOnErrorFunc;
        private boolean initialized = false;

        public MyStompSessionHandler(String url, Function<String, Boolean> connectToServerFunc, Supplier<Boolean> statusFunc,
                                     Runnable terminateOnErrorFunc) {
            this.url = url;
            this.connectToServerFunc = connectToServerFunc;
            this.statusFunc = statusFunc;
            this.terminateOnErrorFunc = terminateOnErrorFunc;
        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("afterConnected(), " + url+ ", " + statusFunc.get());
            // ...
        }

        @Override
        public void handleException(StompSession session, @Nullable StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            System.out.println("handleException(), " + url+ ", " + statusFunc.get());
        }

        /**
         * This implementation is empty.
         */
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            System.out.println("handleTransportError(), " + url + ", " + statusFunc.get());
            terminateOnErrorFunc.run();
        }
    }
}
