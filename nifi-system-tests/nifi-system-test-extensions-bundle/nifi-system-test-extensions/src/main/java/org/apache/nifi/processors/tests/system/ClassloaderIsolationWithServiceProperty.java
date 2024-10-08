/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.tests.system;

import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.components.ClassloaderIsolationKeyProvider;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.context.PropertyContext;
import org.apache.nifi.cs.tests.system.KeyProviderService;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RequiresInstanceClassLoading(cloneAncestorResources = true)
public class ClassloaderIsolationWithServiceProperty extends AbstractProcessor implements ClassloaderIsolationKeyProvider {

    public static final PropertyDescriptor KEY_PROVIDER_SERVICE = new PropertyDescriptor.Builder()
            .name("Key Provider Service")
            .identifiesControllerService(KeyProviderService.class)
            .build();

    static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .build();

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return Collections.singletonList(KEY_PROVIDER_SERVICE);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return Collections.singleton(REL_SUCCESS);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final String classLoaderInstance = this.getClass().getClassLoader().toString();
        flowFile = session.putAttribute(flowFile, "classloader.instance", classLoaderInstance);
        session.transfer(flowFile, REL_SUCCESS);
    }

    @Override
    public String getClassloaderIsolationKey(PropertyContext context) {
        final KeyProviderService service = context.getProperty(KEY_PROVIDER_SERVICE).asControllerService(KeyProviderService.class);
        return service.getKeyField();
    }
}
