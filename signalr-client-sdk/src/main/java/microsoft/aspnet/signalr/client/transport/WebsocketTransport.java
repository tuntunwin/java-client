/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
See License.txt in the project root for license information.
*/

package microsoft.aspnet.signalr.client.transport;

import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.util.Charsetfunctions;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import microsoft.aspnet.signalr.client.ConnectionBase;
import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.Logger;
import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.UpdateableCancellableFuture;
import microsoft.aspnet.signalr.client.http.HttpConnection;

/**
 * Implements the WebsocketTransport for the Java SignalR library
 * Created by stas on 07/07/14.
 */
public class WebsocketTransport extends HttpClientTransport {
    private int msgFrame = 0;
    private String mPrefix;
    private String mContent;
    private static final Gson gson = new Gson();
    WebSocketClient mWebSocketClient;
    private UpdateableCancellableFuture<Void> mConnectionFuture;

    public WebsocketTransport(Logger logger) {
        super(logger);
    }

    public WebsocketTransport(Logger logger, HttpConnection httpConnection) {
        super(logger, httpConnection);
    }

    @Override
    public String getName() {
        return "webSockets";
    }

    @Override
    public boolean supportKeepAlive() {
        return true;
    }

    @Override
    public SignalRFuture<Void> start(ConnectionBase connection, ConnectionType connectionType, final DataResultCallback callback) {
        final String connectionString = connectionType == ConnectionType.InitialConnection ? "connect" : "reconnect";

        final String transport = getName();
        final String connectionToken = connection.getConnectionToken();
        /*final String messageId = connection.getMessageId() != null ? connection.getMessageId() : "";
        final String groupsToken = connection.getGroupsToken() != null ? connection.getGroupsToken() : "";
        final String connectionData = connection.getConnectionData() != null ? connection.getConnectionData() : "";*/

        //Modify by mcs
        String messageId = connection.getMessageId();
        if (messageId == null) messageId = "";
        String groupsToken = connection.getGroupsToken();
        if (groupsToken == null) groupsToken = "";
        String connectionData = connection.getConnectionData();
        if (connectionData == null) connectionData = "";


        String url = null;
        try {
            url = connection.getUrl() + "signalr/" + connectionString + '?'
                    + "connectionData=" + URLEncoder.encode(URLEncoder.encode(connectionData, "UTF-8"), "UTF-8")
                    + "&connectionToken=" + URLEncoder.encode(URLEncoder.encode(connectionToken, "UTF-8"), "UTF-8")
                    + "&groupsToken=" + URLEncoder.encode(groupsToken, "UTF-8")
                    + "&messageId=" + URLEncoder.encode(messageId, "UTF-8")
                    + "&transport=" + URLEncoder.encode(transport, "UTF-8")
                    + "&" + connection.getQueryString(); //Modify by mcs
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        mConnectionFuture = new UpdateableCancellableFuture<Void>(null);

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            mConnectionFuture.triggerError(e);
            return mConnectionFuture;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                mConnectionFuture.setResult(null);
            }

            @Override
            public void onMessage(String s) {
                callback.onData(s);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                mWebSocketClient.close();
            }

            @Override
            public void onError(Exception e) {
                mWebSocketClient.close();
                mConnectionFuture.triggerError(e); //Modify by mcs
            }

            @Override
            public void onFragment(Framedata frame) {
                try {
                    String decodedString = Charsetfunctions.stringUtf8(frame.getPayloadData());

                    /*if (decodedString.equals("]}")) {
                        return;
                    }
                    if (decodedString.endsWith(":[") || null == mPrefix) {
                        mPrefix = decodedString;
                        return;
                    }
                    String simpleConcatenate = mPrefix + decodedString;
                    if (isJSONValid(simpleConcatenate)) {
                        onMessage(simpleConcatenate);
                    } else {
                        String extendedConcatenate = simpleConcatenate + "]}";
                        if (isJSONValid(extendedConcatenate)) {
                            onMessage(extendedConcatenate);
                        } else {
                            log("Invalid json received -> " + decodedString, LogLevel.Critical);
                        }
                    }*/

                    //Modify by mcs
                    //->  ->  ->    1st Frame          2nd Frame             3rd Frame
                    //Frame flow : 1.0,2,2.5 ->     2.5,3.0,4.5 ->      2.5,3.0,4.5,5.0,6.0
                    // 1st Frame include (Prefix:1.0 + Content:2,2.5)
                    msgFrame += 1;
                    if (decodedString.contains("{\"R\":") || null == mPrefix) {
                        mPrefix = decodedString;
                        log("Frame:"+ msgFrame +", PrefixData -> " + mPrefix, LogLevel.Information);
                        return;
                    }
                    mContent = (mContent == null) ? decodedString : mContent + decodedString;
                    log("Frame:"+ msgFrame +", Content -> " + mContent, LogLevel.Information);
                    if (decodedString.contains(",\"I\":\"")) {
                        String simpleConcatenate = mPrefix + mContent;
                        log("Last Frame:"+ msgFrame +", FullContent -> " + simpleConcatenate , LogLevel.Information);
                        if (isJSONValid(simpleConcatenate)) {
                            onMessage(simpleConcatenate);
                        } else {
                            String extendedConcatenate = simpleConcatenate + "]}";
                            if (isJSONValid(extendedConcatenate)) {
                                onMessage(extendedConcatenate);
                            } else {
                                String errorMsg = "Invalid json received -> " + decodedString;
                                log(errorMsg, LogLevel.Critical);
                                onError(new Exception(errorMsg));
                            }
                        }
                        mContent = "";
                    }
                } catch (InvalidDataException e) {
                    e.printStackTrace();
                    mContent = "";
                }
            }
        };
        mWebSocketClient.connect();

        connection.closed(new Runnable() {
            @Override
            public void run() {
                mWebSocketClient.close();
            }
        });

        return mConnectionFuture;
    }

    @Override
    public SignalRFuture<Void> send(ConnectionBase connection, String data, DataResultCallback callback) {
        mWebSocketClient.send(data);
        return new UpdateableCancellableFuture<Void>(null);
    }

    private boolean isJSONValid(String test) {
        try {
            gson.fromJson(test, Object.class);
            return true;
        } catch (com.google.gson.JsonSyntaxException ex) {
            return false;
        }
    }
}