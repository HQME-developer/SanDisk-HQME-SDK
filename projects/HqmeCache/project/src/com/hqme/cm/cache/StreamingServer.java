/** 
* This reference code is an implementation of the IEEE P2200 standard.  It is not
* a contribution to the IEEE P2200 standard.
* 
* Copyright (c) 2011 SanDisk Corporation.  All rights reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use
* this file except in compliance with the License.  You may obtain a copy of the
* License at
* 
*        http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software distributed
* under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
* CONDITIONS OF ANY KIND, either express or implied.
* 
* See the License for the specific language governing permissions and limitations
* under the License.
*/

package com.hqme.cm.cache;

import android.os.RemoteException;

import com.hqme.cm.VSDProperties;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HTTP;

public class StreamingServer implements Runnable {
    private static final String sTag = "StreamingServer";
    
    private static final String tag_Close = "close";
    private static final String tag_Connection = "connection";
    private static final String tag_Get = "get";
    private static final String tag_Head = "head";
    private static final String tag_Host = "host";
    private static final String tag_Post = "post";
    private static final String tag_Query = "?";
    private static final String tag_Range = "range";
    private static final String tag_TokenKey = "token=";
    
    private static final String tag_PlaybackPortNumber = "playback.port";

    private static final int BUFFER_SIZE = 32768;
    private static final int MAX_CLIENTS = 8;
    private static ClientBox clientBox[] = null;

    private static ServerSocket serverSocket = null;
    private static int serverPortNumber = 0;
    private static File serverPortPrefs = null;

    private static boolean isStopping = false;

    //==================================================================================================================================
    /* HTTP */
    public enum HTTP_REQUEST_TYPE {
        RT_UNKNOWN("UNKNOWN"), RT_GET("GET"), RT_POST("POST"), RT_HEAD("HEAD"), RT_CLOSE("CLOSE");

        private String requestType;

        private HTTP_REQUEST_TYPE(String requestType) {
            this.requestType = requestType;
        }

        public String getRequestType() {
            return this.requestType;
        }

        public String toString() {
            return String.format("%s: \"%s\"", getClass().getName(), this.requestType);
        }

        public static HTTP_REQUEST_TYPE getRequestType(String httpHeaderLine) {
            String header = httpHeaderLine == null ? "" : httpHeaderLine.toLowerCase();

            if (header.startsWith(tag_Get))
                return RT_GET;

            if (header.startsWith(tag_Head))
                return RT_HEAD;

            if (header.startsWith(tag_Post))
                return RT_POST;

            if (header.startsWith(tag_Close))
                return RT_CLOSE;

            return RT_UNKNOWN;
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------
    public enum HTTP_RESPONSE_TYPE {
        RT_100_CONTINUE(100, "Continue"),

        RT_200_OK(200, "OK"), RT_201_CREATED(201, "Created"), RT_202_ACCEPTED(202, "Accepted"), RT_203_NON_AUTHORITATIVE_INFORMATION(203, "Non-Authoritative Information"), RT_204_NO_CONTENT(
                204, "No Content"), RT_205_RESET_CONTENT(205, "Reset Content"), RT_206_PARTIAL_CONTENT(206, "Partial Content"),

        RT_300_MULTIPLE_CHOICES(300, "Multiple Choices"), RT_301_MOVED_PERMANENTLY(301, "Moved Permanently"), RT_302_FOUND(302, "Found"), RT_303_SEE_OTHER(303, "See Other"), RT_304_NOT_MODIFIED(
                304, "Not Modified"), RT_305_USE_PROXY(305, "Use Proxy"), RT_307_TEMPORARY_REDIRECT(307, "Temporary Redirect"),

        RT_400_BAD_REQUEST(400, "Bad Request"), RT_401_UNAUTHORIZED(401, "Unauthorized"), RT_402_PAYMENT_REQUIRED(402, "Payment Required"), RT_403_FORBIDDEN(403, "Forbidden"), RT_404_NOT_FOUND(
                404, "Not Found"), RT_405_METHOD_NOT_ALLOWED(405, "Method Not Allowed"), RT_406_NOT_ACCEPTABLE(406, "Not Acceptable"), RT_407_PROXY_AUTHENTICATION_REQUIRED(407,
                "Proxy Authentication Required"), RT_408_REQUEST_TIMEOUT(408, "Request Timeout"), RT_409_CONFLICT(409, "Conflict"), RT_410_GONE(410, "Gone"), RT_411_LENGTH_REQUIRED(
                411, "Length Required"), RT_412_PRECONDITION_FAILED(412, "Precondition Failed"), RT_413_REQUEST_ENTITY_TOO_LARGE(413, "Request Entity Too Large"), RT_414_REQUEST_URI_TOO_LONG(
                414, "Request-URI Too Long"), RT_415_UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"), RT_416_REQUESTED_RANGE_NOT_SATISFIABLE(416,
                "Requested Range Not Satisfiable"), RT_417_EXPECTATION_FAILED(417, "Expectation Failed"),

        RT_500_INTERNAL_SERVER_ERROR(500, "Internal Server Error"), RT_501_NOT_IMPLEMENTED(501, "Not Implemented"), RT_502_BAD_GATEWAY(502, "Bad Gateway"), RT_503_SERVICE_UNAVAILABLE(
                503, "Service Unavailable"), RT_504_GATEWAY_TIMEOUT(504, "Gateway Timeout"), RT_505_HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported");

        private int responseCode;
        private String responseStatus;

        private HTTP_RESPONSE_TYPE(int responseCode, String responseStatus) {
            this.responseCode = responseCode;
            this.responseStatus = responseStatus;
        }

        public int getResponseCode() {
            return this.responseCode;
        }

        public String getResponseStatus() {
            return this.responseStatus;
        }

        public String toString() {
            return String.format("%s: %d \"%s\"", getClass().getName(), this.responseCode, this.responseStatus);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static int getServerPortNumber() {
        return serverPortNumber;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    /* Thread control */
    public void stopServer() {
        UntenCacheService.debugLog(sTag, "stopServer");
        isStopping = true;
        try {
            URL term = new URL("http://localhost:" + serverPortNumber + "/");
            URLConnection conn = term.openConnection();
            conn.setDoOutput(true);
            OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
            out.write("GET /favicon.ico HTTP/1.1");
            out.close();
        } catch (Throwable fault) {
            // UntenCacheService.debugLog(sTag, "stopServer", fault);
        }
    }

    // ==================================================================================================================================
    // ==================================================================================================================================
    /* Implements Interface */
    public void run() {
        if (UntenCacheService.sIsDebugMode) {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
            Thread.currentThread().setName(getClass().getName());
        }

        isStopping = false;
        serverSocket = null;
        serverPortPrefs = new File(UntenCacheService.sPluginContext.getFilesDir(), tag_PlaybackPortNumber);
        try {
            String text = new BufferedReader(new InputStreamReader(new FileInputStream(serverPortPrefs), "UTF16"), 1 << 10).readLine();
            serverPortNumber = Integer.valueOf(text.trim());
        } catch (Throwable ignore) {
            serverPortNumber = 0;
        }

        int retries = 2;
        while (retries-- > 0) {
            try {
                serverSocket = new ServerSocket(serverPortNumber);
                if (serverPortNumber == 0) {
                    serverPortNumber = serverSocket.getLocalPort();
                    try {
                        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(serverPortPrefs), "UTF16");
                        writer.write(serverPortNumber + "\r\n");
                        writer.flush();
                        writer.close();
                    } catch (Throwable ignore) {
                    }
                }
                clientBox = new ClientBox[MAX_CLIENTS];
                retries = 0;

                UntenCacheService.debugLog(sTag, "run: Streaming Media Server is now active on TCP port # %d", serverPortNumber);
                handleRequests();
            } catch (IOException fault) {
                fault.printStackTrace();
                try {
                    serverPortPrefs.delete();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    private void handleRequests() {
        // android.os.Debug.waitForDebugger();

        try {
            Socket client = null;
            while (!isStopping)
                try {
                    client = null;
                    client = serverSocket.accept();
                    
                    // prevent connections from remote internet addresses; clients must playback from localhost sockets only
                     SocketAddress socketAddress = client.getRemoteSocketAddress();
                     if (InetSocketAddress.class.isInstance(socketAddress)) {
                         String hostName = ((InetSocketAddress) socketAddress).getHostName();
                         if (!"localhost".equalsIgnoreCase(hostName)) {
                             // UntenCacheService.debugLog(sTag, "handleRequests: client.getRemoteSocketAddress().getHostName(): %s", hostName);
                             client.close();
                             client = null;
                             continue;
                         }
                     }
                } catch (IOException fault) {
                    isStopping = true;
                } finally {
                    synchronized (clientBox) {
                        if (isStopping) {
                            for (int i = 0; i < clientBox.length; i++) {
                                if (clientBox[i] != null && clientBox[i].thread != null && clientBox[i].thread.isAlive()) {
                                    UntenCacheService.debugLog(sTag, "run: Stopping client # %d", i);
                                    clientBox[i].handler.isStopRequested = true;
                                }
                            }
                        } else if (client != null) {
                            int i = 0;
                            for (i = 0; i < clientBox.length; i++) {
                                if (clientBox[i] == null || !clientBox[i].thread.isAlive()) {
                                    clientBox[i] = new ClientBox();
                                    clientBox[i].handler = new ClientHandler(client, i);
                                    clientBox[i].thread = new Thread(clientBox[i].handler);
                                    clientBox[i].thread.setDaemon(true);
                                    clientBox[i].thread.start();
                                    // UntenCacheService.debugLog(sTag, "handleRequests", "Accepted client # %d", i);
                                    break;
                                }
                            }
                            if (i == clientBox.length) { // End with no slot for it.
                                client.close();
                            }
                        }
                    }
                }
        } catch (Exception fault) {
            fault.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                    serverSocket = null;
                } catch (Throwable ignore) {
                }
            }
            clientBox = null;
        }
    }

    // ==================================================================================================================================
    // ==================================================================================================================================
    /* Structure to hold the threaded client */
    public class ClientBox {
        Thread thread = null;
        ClientHandler handler = null;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    /* Structure to hold parsed request */
    public class RequestBox {
        public StringBuilder requestBody = null;
        public String requestPath = "";
        public String requestParam = "";
        public String requestRange = "";
        
        public String responseMIME = "";
        public String responseProtocol = "";

        public boolean isHttp10() {
            return responseProtocol.endsWith("/1.0");
        }

        public Throwable fault = null; // null = no error

        public HTTP_REQUEST_TYPE requestType = HTTP_REQUEST_TYPE.RT_UNKNOWN;
        public HTTP_RESPONSE_TYPE responseType = HTTP_RESPONSE_TYPE.RT_200_OK;

        public String toString() {
            return String.format(
                    "%s: Protocol = %s : isHttp10 = %s: Param = %s : %s : Mime = %s : Path = %s ",
                    getClass().getName(), responseProtocol, String.valueOf(isHttp10()),
                    requestParam, requestRange, responseMIME, requestPath);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public class ClientHandler implements Runnable {
        // ============================================================
        private int clientBoxIndex = -1;
        private Socket soClient = null;
        
        private OutputStream outStream = null;
        private InputStream inStream = null;
        
        private InputStreamReader inStreamReader = null;
        private BufferedReader inBufferedReader = null;

        public RequestBox request = null;

        private boolean isCloseRequested = false;
        private boolean isStopRequested = false;

        // ============================================================
        public ClientHandler(Socket client, int index) {
            try {
                clientBoxIndex = index;
                soClient = client;
                inStream = client.getInputStream();
                outStream = client.getOutputStream();
            } catch (IOException fault) {
                fault.printStackTrace();
            }
        }

        // ------------------------------------------------------------
        public void ProcessRequest() {
            @SuppressWarnings("unused")
            String host = "";
            try {
                request = new RequestBox();

                inStreamReader = new InputStreamReader(inStream);
                inBufferedReader = new BufferedReader(inStreamReader, 1024);
                String line = inBufferedReader.readLine();
                if (line == null)
                    throw new SocketException();

                // save first line of request for further processing
                request.requestPath = line;

                // keep everything else in request.requestBody (not really necessarily but handy for debugging)
                // request.requestBody = new StringBuilder();
                while (line != null && line.length() > 0) {
                    UntenCacheService.debugLog(sTag, "ProcessRequest:  %s", line); // dump all HTTP Request...
                    
                    String header = line.trim().toLowerCase();

                    if (header.startsWith(tag_Connection)) {
                        isCloseRequested = header.endsWith(tag_Close);
                    } else if (header.startsWith(tag_Host)) {
                        host = line.substring(tag_Host.length() + 1).trim();
                    } else if (header.startsWith(tag_Range)) {
                        request.requestRange = line;
                        request.responseType = HTTP_RESPONSE_TYPE.RT_206_PARTIAL_CONTENT;
                    }

                    // request.requestBody.append(line).append('\n');
                    line = inBufferedReader.readLine();
                }
            } catch (SocketException fault) {
                request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                request.fault = fault;
                // avoid printing debug log info for this fault because client media players commonly close their socket connections "abruptly"
                // request.fault.printStackTrace();
                return;
            } catch (IOException fault) {
                request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                request.fault = fault;
                request.fault.printStackTrace();
                return;
            } catch (Exception fault) {
                request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                request.fault = fault;
                request.fault.printStackTrace();
                return;
            } catch (Throwable fault) {
                request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                request.fault = fault;
                request.fault.printStackTrace();
                return;
            }

            request.requestType = HTTP_REQUEST_TYPE.getRequestType(request.requestPath);

            if (request.requestType == HTTP_REQUEST_TYPE.RT_GET || request.requestType == HTTP_REQUEST_TYPE.RT_POST || request.requestType == HTTP_REQUEST_TYPE.RT_HEAD) {
                // get the http protocol version (e.g. HTTP/1.0 or HTTP/1.1 or later version) and extract the path string between the command and protocol info
                int tail = request.requestPath.indexOf(" HTTP/");
                if (tail >= 0) {
                    request.responseProtocol = request.requestPath.substring(tail + 1).trim();
                    request.requestPath = request.requestPath.substring(request.requestType.getRequestType().length(), tail).trim();
                } else {
                    request.responseType = HTTP_RESPONSE_TYPE.RT_505_HTTP_VERSION_NOT_SUPPORTED; // unsupported protocol or garbage in request line
                }
            } else {
                request.responseType = HTTP_RESPONSE_TYPE.RT_501_NOT_IMPLEMENTED; // unsupported http request method
            }
            
            // extract the Uri query string (if present) from the path string
            int queryPos = request.requestPath.indexOf(tag_Query);
            if (queryPos >= 0) {
                int paramPos = queryPos + tag_Query.length();
                request.requestParam = paramPos < request.requestPath.length() ? request.requestPath.substring(paramPos).trim() : "";
                request.requestPath = request.requestPath.substring(0, queryPos).trim();
            }
        }

        // ------------------------------------------------------------
        boolean isDisconnected = false;

        public void ProcessResponse() {
            if (request == null || request.fault != null || isStopRequested)
                return;
            
            // return 403 Forbidden whenever the playback token is null, e.g. because it was omitted from the Uri, is invalid, has expired, or was requested too many times.
            // return 404 Not Found whenever the file previously used to construct the Uri no longer exists at that location.
            //
            int keyPos = request.requestParam.indexOf(tag_TokenKey);
            PlaybackTokens.PlaybackToken token = keyPos < 0 ? null : PlaybackTokens.getPlaybackToken(request.requestParam.substring(keyPos + tag_TokenKey.length()));
            if (token == null)
                request.responseType = HTTP_RESPONSE_TYPE.RT_403_FORBIDDEN;
            else
                try {
                    if (token.object.size() <= 0)
                        request.responseType = HTTP_RESPONSE_TYPE.RT_404_NOT_FOUND;
                    else
                        request.responseMIME = token.object.getProperty(VSDProperties.SProperty.S_TYPE.name());
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            if (request.responseType == HTTP_RESPONSE_TYPE.RT_200_OK || request.responseType == HTTP_RESPONSE_TYPE.RT_206_PARTIAL_CONTENT) {
                synchronized (token) {
                    try {
                        UntenCacheService.debugLog(sTag, "ProcessResponse: token.object.mObjectPath = %s, MIME = %s", token.object.mObjectPath, request.responseMIME);
                        
                        token.object.open("r", false);
                        long inFileLen = token.object.size();
                        long inRealFileLen = inFileLen;
                        long head = 0, tail = inFileLen;
                        long remain = 0;
                        int block = 0;
                        byte[] buf = new byte[BUFFER_SIZE + 16];

                        // process byte range request
                        if (request.requestRange.length() > 0) {
                            String[] range = request.requestRange.split("[\\x3d\\x2d]"); // e.g.: "Range: bytes=0-123" splits as range[0] = "Range: bytes" : range[1] = "0" : range[2] = "123"
                            try {
                                head = Integer.valueOf(range[1]);
                            } catch (Throwable fault) {
                                head = 0;
                            }
                            try {
                                tail = Integer.valueOf(range[2]);
                            } catch (Throwable fault) {
                                tail = inRealFileLen;
                            }
                            inFileLen = tail - head + 1;
                        }

                        // check if protocol is HTTP/1.0
                        String protocol = request.responseProtocol.substring(0, 4);

                        HttpResponse response = new BasicHttpResponse(new ProtocolVersion(protocol, 1, 1), request.responseType.getResponseCode(), request.responseType.getResponseStatus());
                        response.addHeader(new BasicHeader(HTTP.CONTENT_LEN, Long.toString(inFileLen)));
                        response.addHeader(new BasicHeader(HTTP.CONTENT_TYPE, request.responseMIME));

                        if (request.requestRange.length() > 0) {
                            response.addHeader(new BasicHeader("Content-Range", "bytes " + head + "-" + tail + "/" + inRealFileLen));
                            response.addHeader(new BasicHeader("Accept-Ranges", "bytes"));
                        }

                        if (isCloseRequested || request.isHttp10()) {
                            isCloseRequested = true;
                            response.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, "Close"));
                        }

                        // send reply
                        outStream.write(response.getStatusLine().toString().getBytes());
                        outStream.write("\r\n".getBytes());

                        Header[] headers = response.getAllHeaders();
                        for (int i = 0; i < headers.length; i++) {
                            outStream.write(headers[i].toString().getBytes());
                            outStream.write("\r\n".getBytes());
                            // UntenCacheService.debugLog(sTag, "ProcessResponse", "[Header] " + headers[i]);
                        }
                        outStream.write("\r\n".getBytes());
                        outStream.flush();

                        if (request.requestType == HTTP_REQUEST_TYPE.RT_HEAD)
                            return;

                        remain = inFileLen;
                        block = BUFFER_SIZE;

                        token.object.seek(head, 0);

                        while (remain > 0) {
                            block = remain > BUFFER_SIZE ? BUFFER_SIZE : (int)remain;

                            if (isStopRequested) {
                                UntenCacheService.debugLog(sTag, "ProcessResponse", "Stop requested!");
                                break;
                            }

                            block = token.object.read(buf, (int)block);
                            if (block < 0) {
                                isCloseRequested = true;
                                long error = 0 - block;
                                UntenCacheService.debugLog(sTag, "ProcessResponse @ token.object.read", "Error = %d (0x%08x)", error, error);
                                break;
                            } else {
                                outStream.write(buf, 0, block);
                            }
                            
                            remain -= block;
                        }
                        outStream.flush();
                        return;
                    } catch (FileNotFoundException fault) {
                        request.responseType = HTTP_RESPONSE_TYPE.RT_404_NOT_FOUND;
                        request.fault = fault;
                        request.fault.printStackTrace();
                    } catch (SocketException fault) {
                        isDisconnected = true;
                        request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                        request.fault = fault;
                        // avoid printing debug log info for this fault because client media players commonly close their socket connections "abruptly"
                        // request.fault.printStackTrace();
                    } catch (IOException fault) {
                        isDisconnected = true;
                        request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                        request.fault = fault;
                        request.fault.printStackTrace();
                    } catch (Exception fault) {
                        request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                        request.fault = fault;
                        request.fault.printStackTrace();
                    } catch (Throwable fault) {
                        request.responseType = HTTP_RESPONSE_TYPE.RT_500_INTERNAL_SERVER_ERROR;
                        request.fault = fault;
                        request.fault.printStackTrace();
                    } finally {
                        if (token != null) {
                            try {
                                token.object.close();
                            } catch (RemoteException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            // all code paths which arrive here send an error response
            isCloseRequested = true;
            if (!isDisconnected) {
                String responseStatus = request.responseType.getResponseStatus();
                HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 0), request.responseType.getResponseCode(), responseStatus);
                response.addHeader(new BasicHeader(HTTP.CONTENT_TYPE, "text/html"));
                response.addHeader(new BasicHeader(HTTP.CONTENT_LEN, Integer.toString(responseStatus.length())));

                try {
                    outStream.write(response.getStatusLine().toString().getBytes());
                    outStream.write("\r\n".getBytes());
                    Header[] headers = response.getAllHeaders();
                    for (int i = 0; i < headers.length; i++) {
                        outStream.write(headers[i].toString().getBytes());
                        outStream.write("\r\n".getBytes());
                    }
                    outStream.write("\r\n".getBytes());
                    outStream.write(responseStatus.getBytes());
                    outStream.flush();
                } catch (Throwable fault) {
                    fault.printStackTrace();
                }
            }
        }

        // ------------------------------------------------------------
        public void run() {
            try {
                if (UntenCacheService.sIsDebugMode) {
                    Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 3);
                    Thread.currentThread().setName(getClass().getName() + ".clientBox[" + clientBoxIndex + "]");
                }

                do {
                    ProcessRequest();
                    ProcessResponse();
                } while (!isCloseRequested && !isStopRequested && request != null && request.fault == null && !request.isHttp10());
            } catch (Throwable fault) {
                fault.printStackTrace();
            } finally {
                if (outStream != null)
                    try {
                        outStream.flush();
                        outStream.close();
                    } catch (Throwable fault) {
                        fault.printStackTrace();
                    }

                if (inStream != null)
                    try {
                        inStream.close();
                    } catch (Throwable fault) {
                        fault.printStackTrace();
                    }

                if (inStreamReader != null)
                    try {
                        inStreamReader.close();
                    } catch (Throwable fault) {
                        fault.printStackTrace();
                    }

                if (inBufferedReader != null)
                    try {
                        inBufferedReader.close();
                    } catch (Throwable fault) {
                        fault.printStackTrace();
                    }

                if (soClient != null && !soClient.isClosed())
                    try {
                        soClient.close();
                    } catch (Throwable fault) {
                        fault.printStackTrace();
                    }

                synchronized (clientBox) {
                    clientBox[clientBoxIndex] = null;
                    clientBoxIndex = -1;
                }
            }
        }
    }
}
