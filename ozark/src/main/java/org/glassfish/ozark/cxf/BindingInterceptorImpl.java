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

import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.jaxrs.validation.ValidationUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.validation.BeanValidationInInterceptor;
import org.glassfish.ozark.binding.BindingErrorImpl;
import org.glassfish.ozark.binding.BindingResultImpl;
import org.glassfish.ozark.binding.BindingResultUtils;

import javax.mvc.binding.BindingError;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.glassfish.ozark.binding.BindingResultUtils.getValidInstanceForType;
import static org.glassfish.ozark.binding.BindingResultUtils.updateBindingResultErrors;
import static org.glassfish.ozark.binding.BindingResultUtils.updateBindingResultViolations;

@Provider
public class BindingInterceptorImpl extends BeanValidationInInterceptor implements ContainerRequestFilter {

    public BindingInterceptorImpl() {
        super("pre-invoke");
    }

    public BindingInterceptorImpl(final String phase) {
        super(phase);
    }

    @Override
    protected void handleValidation(final Message message, final Object resourceInstance,
            final Method method, final List<Object> arguments) {

        final BindingOperationInfo bop = message.getExchange().getBindingOperationInfo();

        // Unwrap if necessary
        Object resource = resourceInstance;
        if (BindingResultUtils.isTargetInstanceProxy(resource)) {
            resource = BindingResultUtils.getTargetInstance(resource);
        }

        // If any of the args is a RuntimeException, collect and report errors
        final BindingResultImpl bindingResult = getBindingResultInArgs(arguments);
        RuntimeException firstException = null;
        final Set<BindingError> errors = new HashSet<>();

        final Parameter[] paramsInfo = method.getParameters();

        for (int i = 0; i < arguments.size(); i++) {
            final Object arg = arguments.get(i);
            if (arg instanceof RuntimeException) {
                final Parameter paramInfo = paramsInfo[i];
                final RuntimeException ex = ((RuntimeException) arg);

                final String parameterName = bop.getOperationInfo().getParameterOrdering()
                        .get(i);

                errors.add(new BindingErrorImpl(ex.getCause().toString(), parameterName));
                if (firstException == null) {
                    firstException = ex;
                }
                // Replace parameter with a valid instance or null
                arguments.set(i, getValidInstanceForType(paramInfo.getType()));
            }
        }

        // Update binding result or re-throw first exception if not present
        if (errors.size() > 0) {
            if (!updateBindingResultErrors(resource, errors, bindingResult)) {
                throw firstException;
            }
        }

        try {
            super.handleValidation(message, resourceInstance, method, arguments);
        } catch (ConstraintViolationException cve) {
            // Update binding result or re-throw exception if not present
            if (!updateBindingResultViolations(resource, cve.getConstraintViolations(), bindingResult)) {
                throw cve;
            }
        }

    }

    /**
     * Finds the first argument of type {@code org.glassfish.ozark.binding.BindingResultImpl}.
     * Inspects superclasses in case of proxies.
     *
     * @param args list of arguments to search.
     * @return argument found or {@code null}.
     */
    private BindingResultImpl getBindingResultInArgs(List<Object> args) {
        for (Object a : args) {
            if (a != null) {
                Class<?> argClass = a.getClass();
                do {
                    if (BindingResultImpl.class.isAssignableFrom(argClass)) {
                        return (BindingResultImpl) a;
                    }
                    argClass = argClass.getSuperclass();
                } while (argClass != Object.class);
            }
        }
        return null;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext)
            throws IOException {

        InterceptorChain chain = PhaseInterceptorChain.getCurrentMessage().getInterceptorChain();
        chain.add(this);
    }

    @Override
    protected Object getServiceObject(final Message message) {
        return ValidationUtils.getResourceInstance(message);
    }
}
