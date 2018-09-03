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

package org.wso2.carbon.identity.ext.servlet.filter;

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;


/**
 * Servlet filter used to attach request correlation ID to the diagnostic context.
 */
public class RequestCorrelationIdFilter implements Filter {

    private static final String HEADER_TO_CORRELATION_ID_MAPPING = "HeaderToCorrelationIdMapping";
    private static final String CORRELATION_ID_MDC = "Correlation-ID";
    private Map<String, String> headerToIdMapping;
    private String correlationIdMdc = CORRELATION_ID_MDC;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (filterConfig.getInitParameter(HEADER_TO_CORRELATION_ID_MAPPING) != null) {
            Gson gson = new Gson();
            headerToIdMapping = gson.fromJson(filterConfig.getInitParameter(HEADER_TO_CORRELATION_ID_MAPPING),
                    Map.class);

        }

        if (StringUtils.isNotEmpty(filterConfig.getInitParameter(CORRELATION_ID_MDC))) {
            correlationIdMdc = filterConfig.getInitParameter(CORRELATION_ID_MDC);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        try {
            associateHeadersToThread(servletRequest);
            if (MDC.get(correlationIdMdc) == null) {
                MDC.put(correlationIdMdc, UUID.randomUUID().toString());
            }
            filterChain.doFilter(servletRequest, servletResponse);
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

    private void associateHeadersToThread(ServletRequest servletRequest) {

        if (headerToIdMapping != null && (servletRequest instanceof HttpServletRequest)) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            for (Map.Entry<String, String> entry : headerToIdMapping.entrySet()) {
                String header = entry.getKey();
                String correlationIdName = entry.getValue();
                if (StringUtils.isNotEmpty(header) && StringUtils.isNotEmpty(correlationIdName)) {
                    String headerValue = httpServletRequest.getHeader(header);
                    if (StringUtils.isNotEmpty(headerValue)) {
                        MDC.put(correlationIdName, headerValue);
                    }
                }
            }
        }
    }

    @Override
    public void destroy() {

    }
}
