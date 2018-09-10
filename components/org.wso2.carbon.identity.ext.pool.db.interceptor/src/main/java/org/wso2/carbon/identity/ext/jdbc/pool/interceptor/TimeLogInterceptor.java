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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.*;


/**
 * Time-Logging interceptor for JDBC pool.
 * Logs the time taken to execute the query in each pool-ed connection.
 */
public class TimeLogInterceptor extends AbstractQueryReport{

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
    protected String reportQuery(String query, Object[] args, String name, long start, long delta) {

        String queryDetail = super.reportQuery(query, args, name, start, delta);
        if (timeLog.isDebugEnabled()) {
            timeLog.debug("Query " + queryDetail + " at " + start + " took " + delta + "ms");
        }
        return queryDetail;
    }

    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        Object createStatement = super.createStatement(proxy, method, args, statement, time);
        try {
            if (statement instanceof PreparedStatement) {
                DatabaseMetaData metaData = ((PreparedStatement) statement).getConnection().getMetaData();
                if (timeLog.isDebugEnabled()) {
                    timeLog.debug("Connection String" + metaData.getURL());
                }
            }
        } catch (Exception e) {
            timeLog.error("Cannot get connection string ");
        }
        return createStatement;

    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return super.invoke(proxy, method, args);
    }
}
