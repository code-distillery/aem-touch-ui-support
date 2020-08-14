/*
 *  Copyright 2020 Code Distillery GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.distilledcode.aem.ui.touch.support.impl.requestprocessing;

import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SyntheticHttpServletResponse implements HttpServletResponse {

    private final ResettableBufferedOutputStream outputStream;

    private final Map<String, List<String>> headers = new HashMap<>();

    private DelegatingServletOutputStream servletOutputStream;

    private PrintWriter printWriter;

    private int statusCode = -1;

    private String statusMessage;

    private String characterEncoding = "ISO-8859-1";

    private int contentLength;
    
    private String contentType;

    private Locale locale = Locale.getDefault();

    private boolean isCommitted;

    public SyntheticHttpServletResponse(OutputStream outputStream) {
        this.outputStream = new ResettableBufferedOutputStream(outputStream);
    }

    @Override
    public void addCookie(Cookie cookie) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean containsHeader(String name) {
        return headers.containsKey(name);
    }

    @Override
    public String encodeURL(String url) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String encodeRedirectURL(String url) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String encodeUrl(String url) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String encodeRedirectUrl(String url) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void sendError(int sc, String msg) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void sendError(int sc) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void sendRedirect(String location) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setDateHeader(String name, long date) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addDateHeader(String name, long date) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setHeader(String name, String value) {
        headers.put(name, asArrayList(value));
    }

    @NotNull
    private static List<String> asArrayList(String value) {
        final List<String> values = new ArrayList<>(1);
        values.add(value);
        return values;
    }

    @Override
    public void addHeader(String name, String value) {
        final List<String> values = headers.computeIfAbsent(name, key -> new ArrayList<>());
        values.add(value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addIntHeader(String name, int value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public int getStatusCode() {
        return statusCode == -1 ? 200 : statusCode;
    }

    @Override
    public void setStatus(int statusCode) {
        setStatus(statusCode, null);
    }

    @Override
    public void setStatus(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (printWriter != null) {
            throw new IllegalStateException("Writer was already retrieved before");
        }
        if (servletOutputStream == null) {
            servletOutputStream = new DelegatingServletOutputStream(outputStream);
        }
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (servletOutputStream != null) {
            throw new IllegalStateException("OutputStream was already retrieved before");
        }
        if (printWriter == null) {
            printWriter = new PrintWriter(new OutputStreamWriter(outputStream));
        }
        return printWriter;
    }

    @Override
    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    @Override
    public void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public void setBufferSize(int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int getBufferSize() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isCommitted() {
        return isCommitted;
    }

    @Override
    public void flushBuffer() throws IOException {
        isCommitted = true;
        if (servletOutputStream != null) {
            servletOutputStream.flush();
        } else if (printWriter != null){
            printWriter.flush();
        }
    }

    @Override
    public void reset() {
        if (isCommitted()) {
            throw new IllegalStateException("Response already committed");
        }
        headers.clear();
        statusCode = -1;
        statusMessage = null;
        resetBuffer();
    }

    @Override
    public void resetBuffer() {
        if (isCommitted()) {
            throw new IllegalStateException("Response already committed");
        }
        outputStream.reset();
    }

    @Override
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    private class ResettableBufferedOutputStream extends BufferedOutputStream{
        ResettableBufferedOutputStream(OutputStream outputStream) {
            super(outputStream, 8192);
        }

        void reset() {
            if (SyntheticHttpServletResponse.this.isCommitted) {
                throw new IllegalStateException("Response already committed");
            }
            this.buf = new byte[this.buf.length];
            this.count = 0;
        }

        @Override
        public synchronized void flush() throws IOException {
            SyntheticHttpServletResponse.this.isCommitted = true;
            super.flush();
        }
    }
}
