/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.glassfish.ozark.cxf;

import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Server;
import org.apache.openejb.observer.Observes;
import org.apache.openejb.server.cxf.rs.event.ServerCreated;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.glassfish.ozark.MvcContextImpl;

import static org.glassfish.ozark.util.CdiUtils.newBean;

public class ServerCreatedExtension {

    public void serverCreated(@Observes final ServerCreated event) {
        final WebBeansContext webBeansContext = WebBeansContext.currentInstance();

        if (webBeansContext == null) {
            throw new IllegalStateException("Cannot retrieve the CDI Bean Manager Context");
        }

        final Endpoint endpoint = event.getServer().getEndpoint();
        endpoint.getInInterceptors().add(new BindingInterceptorImpl());

        final BeanManagerImpl beanManager = webBeansContext.getBeanManagerImpl();
        final MvcContextImpl mvc = newBean(beanManager, MvcContextImpl.class);
        mvc.setApplicationPath(event.getWebContext().getContextRoot());
    }

}
