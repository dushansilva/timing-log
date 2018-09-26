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

package org.wso2.carbon.identity.ext.jdbc.pool.interceptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.interceptor.AbstractQueryReport;
import org.wso2.carbon.utils.CarbonUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Time-Logging interceptor for JDBC pool.
 * Logs the time taken to execute the query in each pool-ed connection.
 */
public class TimeLogInterceptor extends AbstractQueryReport {

    private static final Log timeLog = LogFactory.getLog("TIME_LOG");

    @Override
    public void closeInvoked() {

    }

    @Override
    protected void prepareStatement(String s, long l) {

    }

    @Override
    protected void prepareCall(String s, long l) {

    }

    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        try {
            if (CarbonUtils.getServerConfiguration().getFirstProperty("EnableTimingLogs")
                    .equalsIgnoreCase("true")) {

                return invokeProxy(method, args, statement, time);
            } else {
                return statement;
            }

        } catch (Exception var11) {
            timeLog.warn("Unable to create statement proxy for slow query report.");
            return statement;
        }
    }

    private Object invokeProxy(Method method, Object[] args, Object statement, long time) throws Exception {
        Object result = null;
        String name = method.getName();
        String sql = null;
        Constructor<?> constructor = null;

        if (this.compare("prepareStatement", name)) {
            sql = (String) args[0];
            constructor = this.getConstructor(1, PreparedStatement.class);
            if (sql != null) {
                this.prepareStatement(sql, time);
            }
        } else if (!this.compare("prepareCall", name)) {
            return statement;
        }

        result = constructor.newInstance(new StatementProxy(statement, sql));
        return result;
    }

    protected class StatementProxy implements InvocationHandler {
        protected boolean closed = false;
        protected Object delegate;
        protected final String query;

        public StatementProxy(Object parent, String query) {
            this.delegate = parent;
            this.query = query;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            boolean close = TimeLogInterceptor.this.compare("close", name);
            try {
                if (close && this.closed) {
                    return null;
                } else if (TimeLogInterceptor.this.compare("isClosed", name)) {
                    return this.closed;
                } else if (this.closed) {
                    throw new SQLException("Statement closed.");
                } else {
                    boolean process = false;
                    process = TimeLogInterceptor.this.isExecute(method, process);

                    long start = process ? System.currentTimeMillis() : 0L;
                    Object result = null;

                    result = this.delegate != null ? method.invoke(this.delegate, args) : null;


                    long delta = process ? System.currentTimeMillis() - start : -9223372036854775808L;

                    if (process) {
                        TimeLogInterceptor.this.reportQuery(this.query, args, name, start, delta);
                        logQuery(start, delta, name);
                    }

                    if (close) {
                        this.closed = true;
                        this.delegate = null;
                    }

                    return result;
                }
            } catch (Exception e) {
                timeLog.error("Unable get query run-time");
                return null;
            }


        }

        private void logQuery(long start, long delta, String methodName) {
            try {
                if (this.delegate instanceof PreparedStatement) {
                    PreparedStatement preparedStatement = (PreparedStatement) this.delegate;
                    if (preparedStatement.getConnection() != null) {
                        DatabaseMetaData metaData = preparedStatement.getConnection().getMetaData();
                        if (timeLog.isDebugEnabled()) {
                            Map<String, String> log = new LinkedHashMap<>();
                            log.put("delta", Long.toString(delta) + " ms");
                            log.put("callType", "jdbc");
                            log.put("startTime", Long.toString(start));
                            log.put("methodName", methodName);
                            log.put("query", this.query);
                            log.put("connectionUrl", metaData.getURL());
                            timeLog.debug(createLogFormat(log));
                        }
                    }
                }
            } catch (Exception e) {
                timeLog.error("Cannot get connection string ");
            }
        }

        private String createLogFormat(Map<String, String> map) {
            StringBuilder sb = new StringBuilder();
            Object[] keys = map.keySet().toArray();
            for (int i = 0; i < keys.length; i++) {
                sb.append(map.get(keys[i]));
                if (i < keys.length - 1) {
                    sb.append(" | ");
                }
            }

            return sb.toString();
        }
    }
}
