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

package org.wso2.carbon.identity.ext.ldap;

import com.sun.jndi.dns.DnsContextFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

public class BitBetterInitialContextFactory implements InitialContextFactory {

    private InitialContextFactory wrappedInitialContextFactory;
    private static final Class[] proxyInterfaces = new Class[]{Context.class};

    public BitBetterInitialContextFactory() {

        this.wrappedInitialContextFactory = new DnsContextFactory();
    }

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {

        Hashtable environmentForWrappedDNS = new Hashtable();
        environmentForWrappedDNS.putAll(environment);
        environmentForWrappedDNS.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        return wrap(wrappedInitialContextFactory.getInitialContext(environment));
    }

    private Context wrap(Context initialContext) {

        Object proxy = Proxy.newProxyInstance(BitBetterInitialContextFactory.class.getClassLoader(), proxyInterfaces,
                new LdapContextInvocationHandler());
        return (Context) proxy;
    }

    private class LdapContextInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            System.out.println("Ldap " + method.getName() + " args " + args);
            return method.invoke(proxy, args);
        }
    }
}
