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

package org.apache.nifi.web.standard.api.processor;

import org.apache.nifi.web.ComponentDetails;
import org.apache.nifi.web.NiFiWebConfigurationContext;
import org.apache.nifi.web.NiFiWebConfigurationRequestContext;
import org.apache.nifi.web.standard.api.AbstractStandardResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/standard/processor")
public class ProcessorResource extends AbstractStandardResource {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorResource.class);

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/details")
    public Response getDetails(@QueryParam("processorId") final String processorId) {
        final NiFiWebConfigurationContext nifiWebContext = getWebConfigurationContext();
        final ComponentDetails componentDetails = ProcessorWebUtils.getComponentDetails(nifiWebContext, processorId, request);
        final Response.ResponseBuilder response = ProcessorWebUtils.applyCacheControl(Response.ok(componentDetails));
        return response.build();
    }

    @PUT
    @Produces({MediaType.APPLICATION_JSON})
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("/properties")
    public Response setProperties(@QueryParam("processorId") final String processorId, @QueryParam("revisionId") final Long revisionId,
                                  @QueryParam("clientId") final String clientId, @QueryParam("disconnectedNodeAcknowledged") final Boolean isDisconnectionAcknowledged,
                                  Map<String, String> properties) {

        final NiFiWebConfigurationContext nifiWebContext = getWebConfigurationContext();
        final NiFiWebConfigurationRequestContext niFiRequestContext = ProcessorWebUtils.getRequestContext(processorId, revisionId, clientId, isDisconnectionAcknowledged, request);
        final ComponentDetails componentDetails = nifiWebContext.updateComponent(niFiRequestContext, null, properties);
        final Response.ResponseBuilder response = ProcessorWebUtils.applyCacheControl(Response.ok(componentDetails));
        return response.build();
    }

}
