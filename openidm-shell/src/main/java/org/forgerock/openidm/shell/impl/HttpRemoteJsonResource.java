/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openidm.shell.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.codehaus.jackson.map.ObjectMapper;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.FutureResult;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResult;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.UpdateRequest;
import org.restlet.Context;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Conditions;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Preference;
import org.restlet.data.Reference;
import org.restlet.data.Tag;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * @author $author$
 * @version $Revision$ $Date$
 */
public class HttpRemoteJsonResource implements Connection {

    /**
     * Requests that the origin server accepts the entity enclosed in the
     * request as a new subordinate of the resource identified by the request
     * URI.
     *
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5">HTTP
     *      RFC - 9.5 POST</a>
     */
    public static final Method PATCH = new Method("PATCH");

    /**
     * Base reference used for requesting this resource
     */
    private Reference baseReference;

    /** Username used for authentication when accessing the resource */
    private String username = "";

    /** Password used for authentication when accessing the resource */
    private String password = "";

    public HttpRemoteJsonResource() { }

    /**
     * Create a HttpRemoteJsonResource with credentials.
     *
     * @param uri URI of this resource.
     * @param username Username for HTTP basic authentication
     * @param password Password for HTTP basic authentication.
     */
    public HttpRemoteJsonResource(final String uri, final String username, final String password) {
        this.username = username;
        this.password = password;

        baseReference = new Reference(uri);
    }

    @Override
    public JsonValue action(org.forgerock.json.resource.Context context, ActionRequest request)
            throws org.forgerock.json.resource.ResourceException {
        JsonValue params = new JsonValue(request.getAdditionalParameters());
        JsonValue result = handle(request, request.getResourceName(), params);
        return result;
    }

    @Override
    public FutureResult<JsonValue> actionAsync(org.forgerock.json.resource.Context context,
            ActionRequest request, ResultHandler<? super JsonValue> handler) {
        throw new NotImplementedException();
    }

    @Override
    public void close() {
    }

    @Override
    public Resource create(org.forgerock.json.resource.Context context, CreateRequest request)
            throws org.forgerock.json.resource.ResourceException {
        JsonValue result = handle(request, request.getResourceName() + "/" + request.getNewResourceId(), null);
        return new Resource(result.get("_id").asString(), result.get("_rev").asString(), result);
    }

    @Override
    public FutureResult<Resource> createAsync(org.forgerock.json.resource.Context context,
            CreateRequest request, ResultHandler<? super Resource> handler) {
        throw new NotImplementedException();
    }

    @Override
    public Resource delete(org.forgerock.json.resource.Context context, DeleteRequest request)
            throws org.forgerock.json.resource.ResourceException {
        handle(request, request.getResourceName(), null);
        return null;
    }

    @Override
    public FutureResult<Resource> deleteAsync(org.forgerock.json.resource.Context context,
            DeleteRequest request, ResultHandler<? super Resource> handler) {
        throw new NotImplementedException();
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Resource patch(org.forgerock.json.resource.Context context, PatchRequest request)
            throws org.forgerock.json.resource.ResourceException {
        throw new NotImplementedException();
    }

    @Override
    public FutureResult<Resource> patchAsync(org.forgerock.json.resource.Context context,
            PatchRequest request, ResultHandler<? super Resource> handler) {
        throw new NotImplementedException();
    }

    @Override
    public QueryResult query(org.forgerock.json.resource.Context context, QueryRequest request,
            QueryResultHandler handler) throws org.forgerock.json.resource.ResourceException {
        return null;
    }

    @Override
    public QueryResult query(org.forgerock.json.resource.Context context, QueryRequest request,
            Collection<? super Resource> results)
            throws org.forgerock.json.resource.ResourceException {
        return null;
    }

    @Override
    public FutureResult<QueryResult> queryAsync(org.forgerock.json.resource.Context context,
            QueryRequest request, QueryResultHandler handler) {
        throw new NotImplementedException();
    }

    @Override
    public Resource read(org.forgerock.json.resource.Context context, ReadRequest request)
            throws org.forgerock.json.resource.ResourceException {
        JsonValue result = handle(request, request.getResourceName(), null);
        return new Resource(result.get("_id").asString(), result.get("_rev").asString(), result);
    }

    @Override
    public FutureResult<Resource> readAsync(org.forgerock.json.resource.Context context,
            ReadRequest request, ResultHandler<? super Resource> handler) {
        throw new NotImplementedException();
    }

    @Override
    public Resource update(org.forgerock.json.resource.Context context, UpdateRequest request)
            throws org.forgerock.json.resource.ResourceException {
        JsonValue result = handle(request, request.getResourceName(), null);
        return new Resource(result.get("_id").asString(), result.get("_rev").asString(), result);
    }

    @Override
    public FutureResult<Resource> updateAsync(org.forgerock.json.resource.Context context,
            UpdateRequest request, ResultHandler<? super Resource> handler) {
        throw new NotImplementedException();
    }
    
    public ClientResource getClientResource(Reference ref) {
        ClientResource clientResource = new ClientResource(new Context(), new Reference(baseReference, ref));

        List<Preference<MediaType>> acceptedMediaTypes = new ArrayList<Preference<MediaType>>(1);
        acceptedMediaTypes.add(new Preference<MediaType>(MediaType.APPLICATION_JSON));
        clientResource.getClientInfo().setAcceptedMediaTypes(acceptedMediaTypes);
        clientResource.getLogger().setLevel(Level.WARNING);
        
        ChallengeResponse rc = new ChallengeResponse(ChallengeScheme.HTTP_BASIC, username, password);
        clientResource.setChallengeResponse(rc);

        return clientResource;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public JsonValue getResponse(ClientResource clientResource, Representation response) 
            throws org.forgerock.json.resource.ResourceException {
        // Check if the request failed
        if (!clientResource.getStatus().isSuccess()) {
            throw org.forgerock.json.resource.ResourceException.getException(clientResource
                    .getStatus().getCode(), clientResource.getStatus().getDescription(),
                    clientResource.getStatus().getThrowable());
        }

        JsonValue result = null;
        if (null != response && response instanceof EmptyRepresentation == false) {
            try {
                // Parse the response
                result = new JsonValue(new JacksonRepresentation(response, Map.class).getObject());
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
        } else {
            result = new JsonValue(null);
        }
        return result;
    }

    public JsonValue handle(Request request, String id, JsonValue params)
            throws org.forgerock.json.resource.ResourceException {
        Representation response = null;
        ClientResource clientResource = null;
        try {
            Reference remoteRef = new Reference(id);
            
            // Get the client resource corresponding to this request's resource name
            clientResource = getClientResource(remoteRef);

            // Prepare query params
            if (params != null && !params.isNull()) {
                for (Map.Entry<String, Object> entry : params.expect(Map.class).asMap().entrySet()) {
                    if (entry.getValue() instanceof String) {
                        clientResource.addQueryParameter(entry.getKey(), (String) entry.getValue());
                    }
                }
            }

            // Payload
            Representation representation = null;
            JsonValue value = getRequestValue(request);
            if (!value.isNull()) {
                representation = new JacksonRepresentation<Map>(value.expect(Map.class).asMap());
            }

            // ETag
            Conditions conditions = new Conditions();

            switch (request.getRequestType()) {
            case CREATE:
                conditions.setNoneMatch(Arrays.asList(Tag.ALL));
                clientResource.getRequest().setConditions(conditions);
                response = clientResource.put(representation);
                break;
            case READ:
                response = clientResource.get();
                break;
            case UPDATE:
                conditions.setMatch(getTag(((UpdateRequest)request).getRevision()));
                clientResource.getRequest().setConditions(conditions);
                response = clientResource.put(representation);
                break;
            case DELETE:
                conditions.setMatch(Arrays.asList(Tag.ALL));
                clientResource.getRequest().setConditions(conditions);
                response = clientResource.delete();
                break;
            case PATCH:
                conditions.setMatch(getTag(((PatchRequest)request).getRevision()));
                clientResource.getRequest().setConditions(conditions);
                clientResource.setMethod(PATCH);
                clientResource.getRequest().setEntity(representation);
                response = clientResource.handle();
                break;
            case QUERY:
                response = clientResource.get();
                break;
            case ACTION:
                //clientResource.getRequest().setEntity(representation);
                response = clientResource.post(representation);
                break;
            default:
                throw new BadRequestException();
            }

            if (!clientResource.getStatus().isSuccess()) {
                throw org.forgerock.json.resource.ResourceException.getException(clientResource
                        .getStatus().getCode(), clientResource.getStatus().getDescription(),
                        clientResource.getStatus().getThrowable());
            }

            JsonValue result = null;

            if (null != response && response instanceof EmptyRepresentation == false) {
                result = new JsonValue(new JacksonRepresentation(response, Map.class).getObject());
            } else {
                result = new JsonValue(null);
            }
            return result;
        } catch (JsonValueException jve) {
            throw new BadRequestException(jve);
        } catch (ResourceException e) {
            StringBuilder sb = new StringBuilder(e.getStatus().getDescription());
            if (null != clientResource) {
                try {
                    sb.append(" ").append(clientResource.getResponse().getEntity().getText());
                } catch (IOException e1) {
                }
            }
            throw org.forgerock.json.resource.ResourceException.getException(e.getStatus()
                    .getCode(), sb.toString(), e.getCause());
        } catch (Exception e) {
            throw new InternalServerErrorException(e);
        } finally {
            if (null != response) {
                response.release();
            }
        }
    }
    
    private JsonValue getRequestValue(Request request) throws Exception {
        switch (request.getRequestType()) {
        case CREATE:
            return ((CreateRequest)request).getContent();
        case UPDATE:
            return new JsonValue(((UpdateRequest)request).getContent());
        case PATCH:
            ObjectMapper mapper = new ObjectMapper();
            List<PatchOperation> ops = ((PatchRequest)request).getPatchOperations();
            JsonValue value = new JsonValue(new ArrayList<Object>());
            for (PatchOperation op : ops) {
               value.add(new JsonValue(mapper.readValue(op.toString(), Object.class)));
            }
            return value;
        case ACTION:
            JsonValue content = ((ActionRequest)request).getContent();
            if (content != null && !content.isNull()) {
                return content;
            } else {
                return new JsonValue(new HashMap<String, Object>());
            }
        }
        return new JsonValue(null);
    }

    private List<Tag> getTag(String tag) {
        List<Tag> result = new ArrayList<Tag>(1);
        if (null != tag && tag.trim().length() > 0) {
            result.add(Tag.parse(tag));
        }
        return result;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPort(int port) {
        baseReference.setHostPort(port);
    }

    public void setBaseUri(final String baseUri) {
       baseReference = new Reference(baseUri);
    }
}
