#Time logging utilities for each external interaction

## Contains
* JDBC query timing interceptor
* Servlet filter to do request ID correlation

### JDBC time log
1. Build the project
```$cmd
mvn clean install
```
2. Copy "org.wso2.carbon.identity.ext.pool.db.interceptor-0.0.1-SNAPSHOT.jar" to dropins


3. Add following property to datasource configuration

```$xml
<jdbcInterceptors>org.wso2.carbon.identity.ext.jdbc.pool.interceptor.TimeLogInterceptor</jdbcInterceptors>
```

e.g. in master-datasource.xml

```$xml
 <datasource>
            <name>WSO2_CARBON_DB</name>
            <description>The datasource used for registry and user manager</description>
            <jndiConfig>
                <name>jdbc/WSO2CarbonDB</name>
            </jndiConfig>
            <definition type="RDBMS">
                <configuration>
                    <url>jdbc:h2:./repository/database/WSO2CARBON_DB;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=60000</url>
                    <username>wso2carbon</username>
                    <password>wso2carbon</password>
                    <driverClassName>org.h2.Driver</driverClassName>
                    <maxActive>50</maxActive>
                    <maxWait>60000</maxWait>
                    <testOnBorrow>true</testOnBorrow>
                    <validationQuery>SELECT 1</validationQuery>
                    <validationInterval>30000</validationInterval>
                    <defaultAutoCommit>false</defaultAutoCommit>
                    <jdbcInterceptors>org.wso2.carbon.identity.ext.jdbc.pool.interceptor.TimeLogInterceptor</jdbcInterceptors>
                </configuration>
            </definition>
        </datasource>
```

4. Add time-log as rolling file appender in log4j.properties
```properties
# Appender config to put Time Log.
# Appender config to put Time Log.
log4j.logger.TIME_LOG=DEBUG, TIME_LOG
log4j.additivity.TIME_LOG=false
log4j.appender.TIME_LOG = org.apache.log4j.RollingFileAppender
log4j.appender.TIME_LOG.File = ${carbon.home}/repository/logs/${instance.log}/timing${instance.log}.log
log4j.appender.TIME_LOG.Append = false
log4j.appender.TIME_LOG.layout = org.apache.log4j.PatternLayout
log4j.appender.TIME_LOG.layout.ConversionPattern=[%X{somecorId}] [%X{Correlation-ID}] %t - %m%n
```

'somecorId' can be changed according to the value added in init parameters in 
repository/conf/tomcat/web.xml which is shown below.

### Request correlation ID

1. Copy "org.wso2.carbon.identity.ext.servlet.filter-0.0.1-SNAPSHOT.jar" to dropins


2. Add following to "repository/conf/tomcat/web.xml"
```xml
    <filter>
        <filter-name>RequestCorrelationIdFilter</filter-name>
        <filter-class>org.wso2.carbon.identity.ext.servlet.filter.RequestCorrelationIdFilter</filter-class>
        <init-param>
               <param-name>HeaderToCorrelationIdMapping</param-name>
               <param-value>{'someheader':'somecorId','x-corId':'Correlation-ID'}</param-value>
         </init-param>
    </filter>
     <filter-mapping>
        <filter-name>RequestCorrelationIdFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>

```

Here 'someheader' would be the custom header you need to send along with an ID
that you need to show in the log, similarly x-corId also would be the same. 


3. Change the following pattern in the "repository/conf/log4.properties"

```properties
# e.g. [%X{Correlation-ID}]
log4j.appender.CARBON_CONSOLE.layout.ConversionPattern=[%d] %P%5p {%c} - %x [%X{Correlation-ID}] %m%n
...
...
log4j.appender.CARBON_LOGFILE.layout.ConversionPattern=TID: [%T] [%S] [%d] %P%5p {%c} - %x [%X{Correlation-ID}] %m %n   

```
