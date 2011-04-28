/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
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
package org.forgerock.openidm.repo.orientdb.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.openidm.config.installer.JSONConfigInstaller;
import org.forgerock.openidm.objset.ConflictException;
import org.forgerock.openidm.objset.NotFoundException;
import org.forgerock.openidm.objset.ObjectSetException;
import org.forgerock.openidm.objset.PreconditionFailedException;
import org.forgerock.openidm.repo.RepositoryService; 
import org.forgerock.openidm.repo.QueryConstants;
import org.forgerock.openidm.repo.orientdb.impl.DocumentUtil;

import static org.mockito.Mockito.*;

import org.osgi.service.component.ComponentContext;

import org.testng.annotations.*;

import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

import static org.testng.Assert.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;

public class RepoServiceFunctionalTest {

    RepositoryService repo;
    ComponentContext mockContext;
    
    @BeforeClass
    public void activateService() {
        // TODO: Run in embedded felix?
        repo = new OrientDBRepoService();
        ((OrientDBRepoService)repo).dbURL = "local:./target/testdb";
        
        mockContext = mock(ComponentContext.class);
        Dictionary config = getQueryConfig();
        when(mockContext.getProperties()).thenReturn(config);
        ((OrientDBRepoService)repo).activate(mockContext);
    }
     
    @Test(groups = {"repo"})
    public void createAndRead() throws ObjectSetException {
        String uuid = "acf01ec0-66e0-11e0-ae3e-0800200c9a66";
        String id = "/managed/user/" + uuid;
        Map<String, Object> obj = new java.util.HashMap<String, Object>();
        obj.put("firstname", "Johnathan");
        obj.put("lastname", "Wombat");
        obj.put("age", 100);
        obj.put("temperature", -44);
        obj.put("longnumber", Long.MAX_VALUE);
        obj.put("amount", 7654321.98765d);
        obj.put("present", true);
        
        Map addresses = new HashMap<String, Object>();
        Map homeAddress = new HashMap<String, Object>();
        addresses.put("home", homeAddress);
        homeAddress.put("street", "Bourbon street");
        homeAddress.put("city", "St. Louis");
        Map workAddress = new HashMap();
        addresses.put("work", workAddress);
        workAddress.put("street", "Rock road");
        workAddress.put("city", "Ladue");
        obj.put("addresses", addresses);
        
        repo.create(id, obj);
        assertThat(obj).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "0"));
        
        Map<String, Object> result = repo.read(id);
        assertThat(result).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "0"),      // Doc version starts at 0
                entry("firstname", "Johnathan"), 
                entry("lastname", "Wombat"), 
                entry("age", 100),
                entry("temperature", -44),
                entry("longnumber", Long.MAX_VALUE),
                entry("amount", 7654321.98765d),
                entry("present", true));
        
        Object checkAddr = result.get("addresses");
        assertNotNull(checkAddr, "addresses map entry null");
        assertThat(checkAddr).isInstanceOf(Map.class);
        assertThat((Map)checkAddr)
                .hasSize(2);
        Object checkHome = ((Map)checkAddr).get("home");
        assertThat(checkHome).isInstanceOf(Map.class);
        assertThat((Map)checkHome)
               .hasSize(2)
               .includes(entry("street", "Bourbon street"), entry("city", "St. Louis"));
                
        Object checkWork = ((Map)checkAddr).get("work");
        assertThat(checkWork).isInstanceOf(Map.class);
        assertThat((Map)checkWork)
                .hasSize(2)
                .includes(entry("street", "Rock road"), entry("city", "Ladue"));
        
    }
    
    @Test(groups = {"repo"}, expectedExceptions = PreconditionFailedException.class)
    public void duplicateCreate() throws ObjectSetException {
        String uuid = "ccf01ec0-66e0-11e0-ae3e-0800200c9a68";
        String id = "/managed/user/" + uuid;
        Map<String, Object> obj = new java.util.HashMap<String, Object>();
        obj.put("firstname", "Beta");
        
        repo.create(id, obj);
        assertThat(obj).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "0"));
        
        repo.create(id, obj); // Must detect duplicate
        assertTrue(false, "Create with duplicate IDs must fail, but did not for id " + id 
                + ". Second result: " + obj);
        
    }
    
    @Test(groups = {"repo"}) 
    public void updateCurrentObject() throws ObjectSetException {
        String uuid = "bcf01ec0-66e0-11e0-ae3e-0800200c9a67";
        String id = "/managed/user/" + uuid;
        
        Map<String, Object> obj = new java.util.HashMap<String, Object>();
        obj.put("firstname", "Adam");
        obj.put("lastname", "Zeta");
        Map addresses = new HashMap();
        Map homeAddress = new HashMap();
        addresses.put("home", homeAddress);
        homeAddress.put("street", "Main street");
        homeAddress.put("city", "Los Angeles");
        Map workAddress = new HashMap();
        addresses.put("work", workAddress);
        workAddress.put("street", "Business road");
        workAddress.put("city", "Newport Beach");
        obj.put("addresses", addresses);
        
        repo.create(id, obj);
        assertThat(obj).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "0"));
        
        Map<String, Object> objToUpdate = repo.read(id);
        objToUpdate.remove("firstname");
        objToUpdate.put("lastname", "Adler");
        Map retrievedAddr = (Map) objToUpdate.get("addresses");
        assertNotNull(retrievedAddr, "retrieved addresses map entry null"); 
        assertNotNull(retrievedAddr, "addresses map entry null");
        Map retrievedHome = (Map) retrievedAddr.get("home");
        assertNotNull(retrievedHome, "home map entry null");
        retrievedHome.put("city", "Seal Beach");
        retrievedHome.remove("street");
        repo.update(id, (String) objToUpdate.get(DocumentUtil.TAG_REV), objToUpdate);
        
        Map<String, Object> result = repo.read(id);
        
        assertThat(result).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "1"),
                entry("lastname", "Adler")); 
        
        assertThat(result).excludes(entry("firstname", "Adam"));
       
        Object checkAddr = result.get("addresses");
        assertNotNull(checkAddr, "updated addresses map entry null");
        assertThat(checkAddr).isInstanceOf(Map.class);
        assertThat((Map)checkAddr)
                .hasSize(2);
        Object checkHome = ((Map)checkAddr).get("home");
        assertThat(checkHome).isInstanceOf(Map.class);
        assertThat((Map)checkHome)
                .hasSize(1)
                .includes(entry("city", "Seal Beach"))
                .excludes(entry("street", "Main street"));
        
        Object checkWork = ((Map)checkAddr).get("work");
        assertThat(checkWork).isInstanceOf(Map.class);
        assertThat((Map)checkWork)
               .hasSize(2)
               .includes(entry("street", "Business road"), entry("city", "Newport Beach"));
    }
    
    @Test(groups = {"repo"}, enabled=false, expectedExceptions = PreconditionFailedException.class)
    public void updateChangedObject() throws ObjectSetException {
        // check it fails to update an object that has changed since retrieval
        String uuid = "ddddddc0-66e0-11e0-ae3e-0800200ddddd";
        String id = "/managed/user/" + uuid;
        
        Map<String, Object> obj = new java.util.HashMap<String, Object>();
        obj.put("firstname", "Clo");
        Map addresses = new HashMap();
        Map homeAddress = new HashMap();
        addresses.put("home", homeAddress);
        homeAddress.put("street", "Clover street");
        homeAddress.put("city", "San Francisco");
        obj.put("addresses", addresses);
        
        repo.create(id, obj);
        assertThat(obj).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "0"));
        
        Map<String, Object> objToUpdate1 = repo.read(id);
        String originalRev = (String) objToUpdate1.get(DocumentUtil.TAG_REV);

        // In between reading an updating, have another update change the state
        Map<String, Object> objToUpdate2 = repo.read(id);
        objToUpdate2.put("firstname", "Cloes");
        repo.update(id, (String) objToUpdate2.get(DocumentUtil.TAG_REV), objToUpdate2);        
        
        // Updating with an outdated revision must fail
        objToUpdate1.put("firstname", "Cloe");
        repo.update(id, (String) objToUpdate1.get(DocumentUtil.TAG_REV), objToUpdate1);
        
        assertTrue(false, "Update of old revision must fail, but did not.");
    }

    
    @Test(groups = {"repo"})
    public void deleteCurrentObject() throws ObjectSetException {
        String uuid = "aaa01ec0-66e0-11e0-ae3e-0800200c9aaa";
        String id = "/managed/user/" + uuid;
        
        Map<String, Object> obj = new java.util.HashMap<String, Object>();
        obj.put("firstname", "Cesar");
        repo.create(id, obj);
        assertThat(obj).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "0"));
        
        Map<String, Object> objToDelete = repo.read(id);
        assertThat(objToDelete).includes(
            entry(DocumentUtil.TAG_ID, uuid),
            entry(DocumentUtil.TAG_REV, "0"),
            entry("firstname", "Cesar")); 
        
        repo.delete(id, (String) objToDelete.get(DocumentUtil.TAG_REV));
        
        boolean deleteDetected = false;
        try {
            Map<String, Object> deletedObj = repo.read(id);
        } catch (NotFoundException ex) {
            deleteDetected = true;
        }
        assertTrue(deleteDetected, "Delete of object " + id + " failed.");
    }

    @Test(groups = {"repo"}, expectedExceptions = NotFoundException.class)
    public void deleteMissingObject() throws ObjectSetException {
        repo.delete("/managed/user/some-fake-id", "0");
        assertTrue(false, "Delete of unknown ID must fail, but did not.");
    }
    
    @Test(groups = {"repo"}, expectedExceptions = PreconditionFailedException.class)
    public void deleteChangedObject() throws ObjectSetException {
        // check it rejects deleting changed object
        String uuid = "bbb01ec0-66e0-11e0-ae3e-0800200c9bbb";
        String id = "/managed/user/" + uuid;
        
        Map<String, Object> obj = new java.util.HashMap<String, Object>();
        obj.put("firstname", "Cesar");
        repo.create(id, obj);
        assertThat(obj).includes(
                entry(DocumentUtil.TAG_ID, uuid),
                entry(DocumentUtil.TAG_REV, "0"));
        obj.put("lastname", "Added");
        repo.update(id, "0", obj); // Change the object to increase the revision
        
        Map<String, Object> objToDelete = repo.read(id);
        assertThat(objToDelete).includes(
            entry(DocumentUtil.TAG_ID, uuid),
            entry(DocumentUtil.TAG_REV, "1"),
            entry("firstname", "Cesar"),
            entry("lastname", "Added")); 
        
        repo.delete(id, "0"); // Trying to delete old revision must fail
        assertTrue(false, "Delete of changed object must fail, but did not.");
    }
    
    // Test Queries

    String queryUuid1 = "inlinequery-66e0-11e0-ae3e-0800200c9bb1";
    String queryUuid2 = "inlinequery-66e0-11e0-ae3e-0800200c9bb2";
    String queryUuid3 = "inlinequery-66e0-11e0-ae3e-0800200c9bb3";
    String queryUuid4 = "inlinequery-66e0-11e0-ae3e-0800200c9bb4";
    String queryUuid5 = "inlinequery-66e0-11e0-ae3e-0800200c9bb5";
    
    public Dictionary getQueryConfig() {
        Dictionary config = new java.util.Hashtable();
        config.put(JSONConfigInstaller.JSON_CONFIG_PROPERTY, 
                "{" +
                "    \"" + OrientDBRepoService.CONFIG_QUERIES + "\" : {" +
                "        \"query-without-token\" : \"select * from managed/user where lastname like 'Eglo%' and test = 'inlinequery'\"," +
                "        \"query-with-where-token\" : \"select * from managed/user where lastname like 'Eglo%' and test = ${querytype}\"," +
                "        \"query-with-from-token\" : \"select * from ${_resource} where lastname like '${lastname}%' and test = '${querytype}'\"" +
                "    }" +
                "}");
        
        return config;
    }
    
    @Test(groups = {"repo"})
    public void populateQueryData() throws ObjectSetException {
        Map userEx = new HashMap();
        userEx.put("firstname", "Cloe");
        userEx.put("lastname", "Egli");
        userEx.put("test", "inlinequery");
        repo.create("/managed/user/" + queryUuid1, userEx);
        userEx.clear();
        
        userEx.put("firstname", "Andi");
        userEx.put("lastname", "Egloff");
        userEx.put("test", "inlinequery");
        repo.create("/managed/user/" + queryUuid2, userEx);
        userEx.clear();
        
        userEx.put("firstname", "Dorothy");
        userEx.put("lastname", "Smorothy");
        userEx.put("test", "inlinequery");
        repo.create("/managed/user/" + queryUuid3, userEx);
        userEx.clear();
        
        userEx.put("firstname", "Zoe");
        userEx.put("lastname", "Egloff");
        userEx.put("test", "inlinequery");
        repo.create("/managed/user/" + queryUuid4, userEx);
        userEx.clear();

        userEx.put("firstname", "Cloe");
        userEx.put("lastname", "Eglolof");
        userEx.put("test", "inlinequery");
        repo.create("/managed/user/" + queryUuid5, userEx);
        userEx.clear();
    }

    @Test(groups = {"repo"}, dependsOnMethods = {"populateQueryData"})
    public void inlineQuery() throws ObjectSetException {
        Map params = new HashMap();
        //params.put("firstname", "Zebra");
        params.put(QueryConstants.QUERY_EXPRESSION, "select * from managed/user where lastname like 'Eglo%' and test = 'inlinequery'");
        Map result = repo.query("/managed/user", params); 
        List resultSet = (List) result.get(QueryConstants.QUERY_RESULT);
        
        assertThat(resultSet).hasSize(3); 
        assertThat((Map)resultSet.get(0)).includes(entry(DocumentUtil.TAG_ID, queryUuid2));
        assertThat((Map)resultSet.get(1)).includes(entry(DocumentUtil.TAG_ID, queryUuid4));
        assertThat((Map)resultSet.get(2)).includes(entry(DocumentUtil.TAG_ID, queryUuid5));
    }
    
// TODO: Work with OrientDB team as prepared statement seems to have side effects, e.g. the duplicateCreate test 
    @Test(enabled = false, groups = {"repo"}, dependsOnMethods = {"populateQueryData"})
    public void inlineQueryWithWhereToken() throws ObjectSetException {
        Map params = new HashMap();
        params.put("lastname", "Eglo");
        params.put("querytype", "inlinequery");
        params.put(QueryConstants.QUERY_EXPRESSION, "select * from managed/user where lastname like 'Eglo%' and test = ${querytype} ");
        // TODO: work with OrientDB team as OrientDB :token% ':token%' or ':token' does not seem to work        
        // params.put("query-expression", "select * from managed/user where lastname like ${lastname}% and test = ${querytype}");
        Map result = repo.query("/managed/user", params); 
        List resultSet = (List) result.get(QueryConstants.QUERY_RESULT);
        
        assertThat(resultSet).hasSize(3); 
        assertThat((Map)resultSet.get(0)).includes(entry(DocumentUtil.TAG_ID, queryUuid2));
        assertThat((Map)resultSet.get(1)).includes(entry(DocumentUtil.TAG_ID, queryUuid4));
        assertThat((Map)resultSet.get(2)).includes(entry(DocumentUtil.TAG_ID, queryUuid5));
    }

    @Test(groups = {"repo"}, dependsOnMethods = {"populateQueryData"})
    public void inlineQueryWithFromToken() throws ObjectSetException {
        Map params = new HashMap();
        params.put("lastname", "Eglo");
        params.put("querytype", "inlinequery");
        params.put(QueryConstants.QUERY_EXPRESSION, "select * from ${_resource} where lastname like '${lastname}%' and test = '${querytype}'");
        Map result = repo.query("/managed/user", params); 
        List resultSet = (List) result.get(QueryConstants.QUERY_RESULT);
        
        assertThat(resultSet).hasSize(3); 
        assertThat((Map)resultSet.get(0)).includes(entry(DocumentUtil.TAG_ID, queryUuid2));
        assertThat((Map)resultSet.get(1)).includes(entry(DocumentUtil.TAG_ID, queryUuid4));
        assertThat((Map)resultSet.get(2)).includes(entry(DocumentUtil.TAG_ID, queryUuid5));
    }

    @Test(groups = {"repo"}, dependsOnMethods = {"populateQueryData"})
    public void configuredQuery() throws ObjectSetException {
        Map params = new HashMap();
        params.put(QueryConstants.QUERY_ID, "query-without-token");
        Map result = repo.query("/managed/user", params); 
        List resultSet = (List) result.get(QueryConstants.QUERY_RESULT);
        
        assertThat(resultSet).hasSize(3); 
        assertThat((Map)resultSet.get(0)).includes(entry(DocumentUtil.TAG_ID, queryUuid2));
        assertThat((Map)resultSet.get(1)).includes(entry(DocumentUtil.TAG_ID, queryUuid4));
        assertThat((Map)resultSet.get(2)).includes(entry(DocumentUtil.TAG_ID, queryUuid5));
    }
    
    @Test(groups = {"repo"}, dependsOnMethods = {"populateQueryData"})
    public void configuredQueryWithWhereToken() throws ObjectSetException {
        Map params = new HashMap();
        params.put("lastname", "Eglo");
        params.put("querytype", "inlinequery");
        params.put(QueryConstants.QUERY_ID, "query-with-where-token");
        Map result = repo.query("/managed/user", params); 
        List resultSet = (List) result.get(QueryConstants.QUERY_RESULT);
        
        assertThat(resultSet).hasSize(3); 
        assertThat((Map)resultSet.get(0)).includes(entry(DocumentUtil.TAG_ID, queryUuid2));
        assertThat((Map)resultSet.get(1)).includes(entry(DocumentUtil.TAG_ID, queryUuid4));
        assertThat((Map)resultSet.get(2)).includes(entry(DocumentUtil.TAG_ID, queryUuid5));
    }

    
    @Test(groups = {"repo"}, dependsOnMethods = {"populateQueryData"})
    public void configuredQueryWithFromToken() throws ObjectSetException {
        Map params = new HashMap();
        params.put("lastname", "Eglo");
        params.put("querytype", "inlinequery");
        params.put(QueryConstants.QUERY_ID, "query-with-from-token");
        Map result = repo.query("/managed/user", params); 
        List resultSet = (List) result.get(QueryConstants.QUERY_RESULT);
        
        assertThat(resultSet).hasSize(3); 
        assertThat((Map)resultSet.get(0)).includes(entry(DocumentUtil.TAG_ID, queryUuid2));
        assertThat((Map)resultSet.get(1)).includes(entry(DocumentUtil.TAG_ID, queryUuid4));
        assertThat((Map)resultSet.get(2)).includes(entry(DocumentUtil.TAG_ID, queryUuid5));
    }

    // Test ideas: 
    // - nested doc query
    // - fields token substitution when supported
    // - check exception - invalid query id
    // - check exception - missing query token
    // - check exception - invalid query expression
    // Test pre-defined query

    
    @AfterClass
    public void deactivateService() {
        if (repo != null) {
            ((OrientDBRepoService)repo).deactivate(mockContext);
        }
    }
}