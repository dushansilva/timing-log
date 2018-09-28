/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.ext.servlet.valve;

import com.google.gson.Gson;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tomcat valve which adds MDC from a header value received.
 * The header and its MDC can be configured.
 * <p>
 * By default a HTTP header "activityid" is added to MDC "Correlation-ID"
 * <p>
 * The header and MDC can be configured in tomcat valve configuration like,
 * <code>
 *
 * </code>
 */
public class RequestCorrelationIdValve extends ValveBase {

    private static final String CORRELATION_ID_MDC = "Correlation-ID";
    private Map<String, String> headerToIdMapping;
    private Map<String, String> queryToIdMapping;
    private static List<String> toRemoveFromThread = new ArrayList<>();
    private String correlationIdMdc = CORRELATION_ID_MDC;
    private String headerToCorrelationIdMapping;
    private String queryToCorrelationIdMapping;
    private String configuredCorrelationIdMdc;

    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();
        Gson gson = new Gson();
        if (StringUtils.isNotEmpty(headerToCorrelationIdMapping)) {
            headerToIdMapping = gson.fromJson(this.headerToCorrelationIdMapping, Map.class);
            toRemoveFromThread.addAll(headerToIdMapping.values());
        }

        if (StringUtils.isNotEmpty(queryToCorrelationIdMapping)) {
            queryToIdMapping = gson.fromJson(this.queryToCorrelationIdMapping, Map.class);
            toRemoveFromThread.addAll(queryToIdMapping.values());
        }

        if (StringUtils.isNotEmpty(configuredCorrelationIdMdc)) {
            correlationIdMdc = configuredCorrelationIdMdc;
        }
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        try {
            associateHeadersToThread(request);
            if (MDC.get(correlationIdMdc) == null) {
                associateQueryParamsToThread(request);
            }
            if (MDC.get(correlationIdMdc) == null) {
                MDC.put(correlationIdMdc, UUID.randomUUID().toString());
            }
            getNext().invoke(request, response);
        } finally {
            disAssociateFromThread();
            MDC.remove(correlationIdMdc);
        }
    }

    /**
     * Remove all headers values associated with the thread.
     */

    private void disAssociateFromThread() {

        if (toRemoveFromThread != null) {
            for (String correlationIdName : toRemoveFromThread) {
                MDC.remove(correlationIdName);
            }
        }
    }

    /**
     * Search though the list of query params configured against query params received.
     *
     * @param servletRequest request received
     */
    private void associateQueryParamsToThread(ServletRequest servletRequest) {

        if (queryToIdMapping != null && (servletRequest instanceof HttpServletRequest)) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

            for (Map.Entry<String, String> entry : queryToIdMapping.entrySet()) {
                String queryConfigured = entry.getKey();
                String correlationIdName = entry.getValue();
                if (StringUtils.isNotEmpty(queryConfigured) && StringUtils.isNotEmpty(correlationIdName)) {
                    Enumeration<String> parameterNames = httpServletRequest.getParameterNames();
                    while (parameterNames.hasMoreElements()) {
                        String queryReceived = parameterNames.nextElement();
                        setQueryCorrelationIdValue(queryReceived, queryConfigured, httpServletRequest, correlationIdName);
                    }
                }
            }
        }
    }

    /**
     * Search though the list of headers configured against headers received.
     *
     * @param servletRequest request received
     */
    private void associateHeadersToThread(ServletRequest servletRequest) {

        if (headerToIdMapping != null && (servletRequest instanceof HttpServletRequest)) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

            for (Map.Entry<String, String> entry : headerToIdMapping.entrySet()) {
                String headerConfigured = entry.getKey();
                String correlationIdName = entry.getValue();
                if (StringUtils.isNotEmpty(headerConfigured) && StringUtils.isNotEmpty(correlationIdName)) {
                    Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
                    while (headerNames.hasMoreElements()) {
                        String headerReceived = headerNames.nextElement();
                        setHeaderCorrelationIdValue(headerReceived, headerConfigured, httpServletRequest, correlationIdName);
                    }
                }
            }
        }
    }

    /**
     * Set correlationId value received via header to the thread
     *
     * @param headerReceived     header received in request
     * @param headerConfigured   header configured in the valve
     * @param httpServletRequest request received
     * @param correlationIdName  correlationId
     */
    private void setHeaderCorrelationIdValue(String headerReceived, String headerConfigured,
                                             HttpServletRequest httpServletRequest, String correlationIdName) {

        if (StringUtils.isNotEmpty(headerReceived) && headerReceived.equalsIgnoreCase(headerConfigured)) {
            String headerValue = httpServletRequest.getHeader(headerReceived);
            if (StringUtils.isNotEmpty(headerValue)) {
                MDC.put(correlationIdName, headerValue);
            }
        }
    }

    /**
     * Set correlationId value received via query param to the thread
     *
     * @param queryReceived      query received in request
     * @param queryConfigured    query configured in the valv
     * @param httpServletRequest request received
     * @param correlationIdName  correlationId
     */
    private void setQueryCorrelationIdValue(String queryReceived, String queryConfigured,
                                            HttpServletRequest httpServletRequest, String correlationIdName) {

        if (StringUtils.isNotEmpty(queryReceived) && queryReceived.equalsIgnoreCase(queryConfigured)) {
            String queryValue = httpServletRequest.getParameter(queryReceived);
            if (StringUtils.isNotEmpty(queryValue)) {
                MDC.put(correlationIdName, queryValue);
            }
        }
    }

    public void setHeaderToCorrelationIdMapping(String headerToCorrelationIdMapping) {

        this.headerToCorrelationIdMapping = headerToCorrelationIdMapping;
    }

    public void setQueryToCorrelationIdMapping(String queryToCorrelationIdMapping) {

        this.queryToCorrelationIdMapping = queryToCorrelationIdMapping;
    }

    public void setConfiguredCorrelationIdMdc(String configuredCorrelationIdMdc) {

        this.configuredCorrelationIdMdc = configuredCorrelationIdMdc;
    }
}
