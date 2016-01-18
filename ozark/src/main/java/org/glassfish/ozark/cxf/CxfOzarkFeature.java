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

import org.apache.cxf.jaxrs.impl.ResourceInfoImpl;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.glassfish.ozark.MvcContextImpl;
import org.glassfish.ozark.core.ViewRequestFilter;
import org.glassfish.ozark.core.ViewResponseFilter;
import org.glassfish.ozark.core.ViewableWriter;
import org.glassfish.ozark.security.CsrfProtectFilter;
import org.glassfish.ozark.security.CsrfValidateInterceptor;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.mvc.annotation.Controller;
import javax.servlet.ServletContext;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.glassfish.ozark.util.AnnotationUtils.getAnnotation;
import static org.glassfish.ozark.util.CdiUtils.newBean;

@Provider
public class CxfOzarkFeature implements DynamicFeature {

    @Context
    private ServletContext servletContext;

    private boolean isController(Class<?> c) {
        return getAnnotation(c, Controller.class) != null ||
                Arrays.asList(c.getMethods()).stream().anyMatch(m -> getAnnotation(m, Controller.class) != null);
    }

    @Override
    public void configure(final ResourceInfo resourceInfo, final FeatureContext context) {
        final Configuration config = context.getConfiguration();

        if (! isController(resourceInfo.getResourceClass())) {
            return;
        }

        registerIfNeeded(context, config, ViewRequestFilter.class);
        registerIfNeeded(context, config, ViewResponseFilter.class);
        registerIfNeeded(context, config, ViewableWriter.class);
        registerIfNeeded(context, config, BindingInterceptorImpl.class);
        registerIfNeeded(context, config, CsrfValidateInterceptor.class);
        registerIfNeeded(context, config, CsrfProtectFilter.class);

        final OperationResourceInfo ori = getORI(resourceInfo);

        if (ori != null && ori.getProduceTypes().isEmpty()) {
            // add MediaType.TEXT_HTML_TYPE
            ori.getProduceTypes().add(MediaType.TEXT_HTML_TYPE);
        }

        // Initialize application config object in Mvc class
        final BeanManager bm = CDI.current().getBeanManager();
        final MvcContextImpl mvc = newBean(bm, MvcContextImpl.class);
        mvc.setConfig(config);
    }

    private OperationResourceInfo getORI(final ResourceInfo resourceInfo) {
        if (! ResourceInfoImpl.class.isInstance(resourceInfo)) {
            return null;
        }

        try {
            final ResourceInfoImpl info = ResourceInfoImpl.class.cast(resourceInfo);
            final Field ori = ResourceInfoImpl.class.getDeclaredField("ori");
            ori.setAccessible(true);
            return OperationResourceInfo.class.cast(ori.get(info));
        } catch (final Throwable t) {
            return null;
        }
    }

    private void registerIfNeeded(final FeatureContext context, final Configuration config, final Class<?> componentClass) {
        final BeanManager bm = CDI.current().getBeanManager();

        if (!config.isRegistered(componentClass)) {
            context.register(newBean(bm, componentClass));
        }
    }
}
