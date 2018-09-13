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

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.interceptor.AbstractQueryReport;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.HashMap;
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
            Object result = null;
            String name = method.getName();
            String sql = null;
            Constructor<?> constructor = null;
            if (this.compare("createStatement", name)) {
                constructor = this.getConstructor(0, Statement.class);
            } else if (this.compare("prepareStatement", name)) {
                sql = (String) args[0];
                constructor = this.getConstructor(1, PreparedStatement.class);
                if (sql != null) {
                    this.prepareStatement(sql, time);
                }
            } else {
                if (!this.compare("prepareCall", name)) {
                    return statement;
                }
                sql = (String) args[0];
                constructor = this.getConstructor(2, CallableStatement.class);
                this.prepareCall(sql, time);
            }
            result = constructor.newInstance(new StatementProxy(statement, sql));
            return result;
        } catch (Exception var11) {
            timeLog.warn("Unable to create statement proxy for slow query report.", var11);
            return statement;
        }

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

                try {
                    result = method.invoke(this.delegate, args);
                } catch (Throwable var13) {
                    TimeLogInterceptor.this.reportFailedQuery(this.query, args, name, start, var13);
                    if (var13 instanceof InvocationTargetException && var13.getCause() != null) {
                        throw var13.getCause();
                    }

                    throw var13;
                }

                long delta = process ? System.currentTimeMillis() - start : -9223372036854775808L;

                if (process) {
                    TimeLogInterceptor.this.reportQuery(this.query, args, name, start, delta);
                }


                logQuery(start, delta);
                if (close) {
                    this.closed = true;
                    this.delegate = null;
                }

                return result;
            }
        }

        private void logQuery(long start, long delta) {
            try {
                if (delegate instanceof PreparedStatement) {
                    PreparedStatement preparedStatement = (PreparedStatement) this.delegate;
                    if (preparedStatement.getConnection() != null) {
                        DatabaseMetaData metaData = preparedStatement.getConnection().getMetaData();
                        if (timeLog.isDebugEnabled()) {
                            Gson gson = new Gson();
                            Map<String, String> log = new HashMap<>();
                            log.put("query", this.query);
                            log.put("startTime", Long.toString(start));
                            log.put("delta", Long.toString(delta));
                            log.put("connectionUrl", metaData.getURL());
                            timeLog.debug(gson.toJson(log));
                        }
                    }
                }
            } catch (Exception e) {
                timeLog.error("Cannot get connection string ");
            }
        }
    }
}
