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
package org.forgerock.openidm.sync.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author gael
 */
public class ReconStats {

    private String reconId = "";
    private String reconName = "";
    private long startTime = 0;
    private long endTime = 0;
    private long allIdsStartTime = 0;
    private long allIdsEndTime = 0;
    private String startTimestamp = "";
    private String endTimestamp = "";
    private Map<Situation, List<String>> ids = new EnumMap<Situation, List<String>>(Situation.class);
    private SimpleDateFormat sdformat = new SimpleDateFormat();
    public long entries = 0;
    public List<String> notValid = null;

    public ReconStats(String reconId, String reconName) {
        this.reconId = reconId;
        this.reconName = reconName;
        ids.put(Situation.CONFIRMED, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.FOUND, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.ABSENT, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.AMBIGUOUS, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.MISSING, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.UNQUALIFIED, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.UNASSIGNED, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.SOURCE_MISSING, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.SOURCE_IGNORED, Collections.synchronizedList(new ArrayList<String>()));
        ids.put(Situation.TARGET_IGNORED, Collections.synchronizedList(new ArrayList<String>()));
        notValid = new ArrayList<String>();
    }

    public void start() {
        startTime = System.currentTimeMillis();
        startTimestamp = sdformat.format(new Date());
    }
    
    public void end() {
        endTime = System.currentTimeMillis();
        endTimestamp = sdformat.format(new Date());
    }

    public void startAllIds() {
        allIdsStartTime = System.currentTimeMillis();
    }

    public void endAllIds() {
        allIdsEndTime = System.currentTimeMillis();
    }

    public void addSituation(String id, Situation situation) {
        if (situation != null) {
            List<String> situationIds = ids.get(situation);
            if (situationIds != null) {
                situationIds.add(id);
            }
        }
    }

    // TODO: phase out notValid and replace with source ignored, target ignored, unqualified
    // situation processing
    public void addNotValid(String id) {
        notValid.add(id);
    }

    public void addAction(String id, Action action) {
    }

    public Map<String, Object> asMap() {
        Map<String, Object> results = new HashMap();

        results.put("startTime", startTimestamp);
        results.put("endTime", endTimestamp);
        results.put("duration", endTime - startTime);
        results.put("allIds", allIdsEndTime - allIdsStartTime);
        results.put("entries", entries);
        results.put("reconId", reconId);
        results.put("reconName", reconName);

        Map<String, Object> nv = new HashMap();
        nv.put("count", notValid.size());
        nv.put("ids", notValid);
        results.put("NOTVALID", nv);

        for (Entry<Situation, List<String>> e : ids.entrySet()) {
            Map<String, Object> res = new HashMap();
            res.put("count", e.getValue().size());
            res.put("ids", e.getValue());
            results.put(e.getKey().name(), res);
        }

        return results;
    }
    
    public static String simpleSummary(ReconStats globalStats, ReconStats sourceStats, ReconStats targetStats) {
        Map<String, Integer> simpleSummary = new HashMap<String, Integer>();
        updateSummary(simpleSummary, globalStats);
        updateSummary(simpleSummary, sourceStats);
        updateSummary(simpleSummary, targetStats);
        
        StringBuilder sb = new StringBuilder();
        for (Entry<String, Integer> e : simpleSummary.entrySet()) {
            sb.append(e.getKey());
            sb.append(": ");
            sb.append(e.getValue());
            sb.append(" ");
        }
        return sb.toString();
    }
    
    private static void updateSummary(Map<String, Integer> simpleSummary, ReconStats stat) {
        if (stat != null) {
            for (Entry<Situation, List<String>> e : stat.ids.entrySet()) {
                String key = e.getKey().name();
                Integer existing = simpleSummary.get(key);
                if (existing == null) {
                    existing = Integer.valueOf(0);
                }
                Integer updated = existing + e.getValue().size();
                simpleSummary.put(key, updated);
            }
        }
    }
}
