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

import com.google.common.base.Objects;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SyntheticHttpServletRequest implements HttpServletRequest {

    private final Data data;

    private final Map<String, Object> attributes = new HashMap<>();

    private String characterEncoding;

    public static Builder builder(String url) {
        return new Builder("GET", url);
    }

    public static Builder builder(String method, String url) {
        return new Builder(method, url);
    }

    private SyntheticHttpServletRequest(Data data) {
        this.data = data;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration getAttributeNames() {
        return IteratorUtils.asEnumeration(attributes.keySet().iterator());
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    @Override
    public int getContentLength() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getContentType() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ServletInputStream getInputStream() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getParameter(String name) {
        final String[] values = data.parameters.get(name);
        return values.length > 0 ? values[0] : null;
    }

    @Override
    public Enumeration getParameterNames() {
        return IteratorUtils.asEnumeration(data.parameters.keySet().iterator());
    }

    @Override
    public String[] getParameterValues(String name) {
        return data.parameters.get(name);
    }

    @Override
    public Map getParameterMap() {
        return Collections.unmodifiableMap(data.parameters);
    }

    @Override
    public String getProtocol() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getScheme() {
        // TODO: implement getScheme
        return "http";
    }

    @Override
    public String getServerName() {
        // TODO: implement getServerName
        return "localhost";
    }

    @Override
    public int getServerPort() {
        // TODO: implement getServerPort
        return 4502;
    }

    @Override
    public BufferedReader getReader() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getRemoteAddr() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getRemoteHost() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public Locale getLocale() {
        return data.locale;
    }

    @Override
    public Enumeration getLocales() {
        return IteratorUtils.asEnumeration(Collections.singletonList(getLocale()).iterator());
    }

    @Override
    public boolean isSecure() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getRealPath(String path) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int getRemotePort() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getLocalName() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getLocalAddr() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int getLocalPort() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getAuthType() {
        return data.authType;
    }

    @Override
    public Cookie[] getCookies() {
        return data.cookies;
    }

    @Override
    public long getDateHeader(String name) {
        final String header = getHeader(name);
        if (header == null) {
            return -1;
        }
        try {
            final FastDateFormat instance = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
            return instance.parse(header).getTime();
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String getHeader(String name) {
        final String[] values = data.headers.get(name.toLowerCase(Locale.ROOT));
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public Enumeration getHeaders(String name) {
        final String[] values = data.headers.get(name.toLowerCase(Locale.ROOT));
        return IteratorUtils.asEnumeration(Arrays.asList(values).iterator());
    }

    @Override
    public Enumeration getHeaderNames() {
        return IteratorUtils.asEnumeration(data.headers.keySet().iterator());
    }

    @Override
    public int getIntHeader(String name) {
        final String header = getHeader(name);
        return header == null ? -1 : Integer.parseInt(header);
    }

    @Override
    public String getMethod() {
        return data.method;
    }

    @Override
    public String getPathInfo() {
        return data.pathInfo;
    }

    @Override
    public String getPathTranslated() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getContextPath() {
        // TODO: implement getContextPath
        return "";
    }

    @Override
    public String getQueryString() {
        // TODO: distinguish between query params and post params?
        final StringBuilder queryString = new StringBuilder();
        for (String key : data.parameters.keySet()) {
            for (String value : data.parameters.get(key)) {
                queryString.append(key).append("=").append(value).append("&");
            }
        }
        if (queryString.length() > 0) {
            queryString.setLength(queryString.length() - 1);
        }
        return queryString.toString();
    }

    @Override
    public String getRemoteUser() {
        return "admin";
    }

    @Override
    public boolean isUserInRole(String role) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getRequestedSessionId() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getRequestURI() {
        return getContextPath() +
                getServletPath() +
                Objects.firstNonNull(getPathInfo(), "");
    }

    @Override
    public StringBuffer getRequestURL() {
        return new StringBuffer()
                .append(getScheme()).append("://")
                .append(getServerName()).append(":")
                .append(getServerPort())
                .append(getRequestURI());
    }

    @Override
    public String getServletPath() {
        return data.servletPath;
    }

    @Override
    public HttpSession getSession(boolean create) {
        if (!create) {
            return null;
        }
        return NullHttpSession.INSTANCE;
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public static class Builder {

        private final Data data;

        private Builder(String method, String url) {
            this.data = new Data();
            this.data.method = method.toUpperCase(Locale.ROOT);
            parseUrl(url);
        }

        private void parseUrl(String url) {
            data.servletPath = StringUtils.substringBefore(url, "?");
            final String queryString = StringUtils.substringAfter(url, "?");
            final String[] params = StringUtils.splitPreserveAllTokens(queryString, "&=");
            for (int i = 1; i < params.length; i+=2) {
                addParameterValues(params[i - 1], params[i]);
            }
        }

        Builder withPathInfo(String pathInfo) {
            this.data.pathInfo = pathInfo;
            return this;
        }

        Builder addParameterValues(String name, String... values) {
            data.parameters.compute(name, (key, oldValue) -> ArrayUtils.addAll(oldValue, values));
            return this;
        }

        Builder withParameter(String name, String... values) {
            data.parameters.put(name, values);
            return this;
        }

        Builder addHeaderValues(String name, String... values) {
            data.headers.compute(name.toLowerCase(Locale.ROOT), (key, oldValue) -> ArrayUtils.addAll(oldValue, values));
            return this;
        }

        Builder withHeader(String name, String... values) {
            data.headers.put(name.toLowerCase(Locale.ROOT), values);
            return this;
        }

        Builder withLocale(Locale locale) {
            data.locale = locale;
            return this;
        }

        public Builder withCookies(Cookie[] cookies) {
            data.cookies = cookies;
            return this;
        }

        public HttpServletRequest build() {
            return new SyntheticHttpServletRequest(Data.copy(data));
        }

        public Builder withAuthType(String authType) {
            data.authType = authType;
            return this;
        }
    }

    private static class Data {

        String method;

        String servletPath = "";

        String pathInfo;

        Locale locale = Locale.getDefault();

        Cookie[] cookies = new Cookie[0];

        String authType;

        final Map<String, String[]> headers = new HashMap<>();

        final Map<String, String[]> parameters = new HashMap<>();

        public static Data copy(Data data) {
            final Data copy = new Data();
            copy.method = data.method;
            copy.servletPath = data.servletPath;
            copy.pathInfo = data.pathInfo;
            copy.locale = data.locale;
            copy.headers.putAll(data.headers);
            copy.parameters.putAll(data.parameters);
            copy.cookies = Arrays.copyOf(data.cookies, data.cookies.length);
            copy.authType = data.authType;
            return copy;
        }
    }

    private static class NullHttpSession implements HttpSession {

        static final HttpSession INSTANCE = new NullHttpSession();

        @Override
        public long getCreationTime() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String getId() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public long getLastAccessedTime() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public ServletContext getServletContext() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public int getMaxInactiveInterval() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public HttpSessionContext getSessionContext() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Object getAttribute(String name) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Object getValue(String name) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Enumeration getAttributeNames() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String[] getValueNames() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void setAttribute(String name, Object value) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void putValue(String name, Object value) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void removeAttribute(String name) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void removeValue(String name) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void invalidate() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isNew() {
            throw new UnsupportedOperationException("not implemented");
        }
    }
}
