package org.wso2.carbon.identity.ext.servlet.valve;

import com.google.gson.Gson;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

public class RequestCorrelationIdValve extends ValveBase {
    private static final String HEADER_TO_CORRELATION_ID_MAPPING = "HeaderToCorrelationIdMapping";
    private static final String QUERY_TO_CORRELATION_ID_MAPPING = "QueryToCorrelationIdMapping";
    private static final String CORRELATION_ID_MDC = "Correlation-ID";
    private Map<String, String> headerToIdMapping;
    private Map<String, String> queryToIdMapping;
    private String correlationIdMdc = CORRELATION_ID_MDC;
    private String headerToCorrelationIdMapping;
    private String queryToCorrelationIdMapping;
    private String configuredCorrelationIdMdc;

    private void initialize() {

        Gson gson = new Gson();
        if (StringUtils.isNotEmpty(headerToCorrelationIdMapping)) {
            headerToIdMapping = gson.fromJson(this.headerToCorrelationIdMapping, Map.class);
        }

        if (StringUtils.isNotEmpty(queryToCorrelationIdMapping)) {
            queryToIdMapping = gson.fromJson(this.queryToCorrelationIdMapping, Map.class);
        }

        if (StringUtils.isNotEmpty(configuredCorrelationIdMdc)) {
            correlationIdMdc = configuredCorrelationIdMdc;
        }
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        try {
            initialize();
            associateHeadersToThread(request);
            if (MDC.get(correlationIdMdc) == null) {
                MDC.put(correlationIdMdc, UUID.randomUUID().toString());
            }
            getNext().invoke(request, response);
        } finally {
            disAssociateHeadersFromThread();
            MDC.remove(correlationIdMdc);
        }
    }

    private void disAssociateHeadersFromThread() {
        if (headerToIdMapping != null) {
            for (String correlationIdName : headerToIdMapping.values()) {
                MDC.remove(correlationIdName);
            }
        }
    }

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
            if (MDC.get(correlationIdMdc) == null) {
                associateQueryParamsToThread(servletRequest);
            }
        }
    }

    private void setHeaderCorrelationIdValue(String headerReceived, String headerConfigured,
                                             HttpServletRequest httpServletRequest, String correlationIdName) {
        if (StringUtils.isNotEmpty(headerReceived) && headerReceived.equalsIgnoreCase(headerConfigured)) {
            String headerValue = httpServletRequest.getHeader(headerReceived);
            if (StringUtils.isNotEmpty(headerValue)) {
                MDC.put(correlationIdName, headerValue);
            }
        }
    }

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
