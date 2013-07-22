/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013 ForgeRock Inc.
 */

package org.forgerock.openidm.filterregistration;

import org.forgerock.json.fluent.JsonValue;
import org.osgi.framework.ServiceRegistration;

/**
 * Interface for registering servlet filters in OSGi.
 *
 * @author Phill Cunnington
 */
public interface ServletFilterRegistration {

    /**
     * Parses the given servlet filter configuration and registers a servlet filter in OSGi.
     *
     * @param config The servlet filter configuration.
     * @return The ServiceRegistration.
     * @throws Exception If a problem occurs registering the servlet filter.
     */
    ServiceRegistration registerFilter(JsonValue config) throws Exception;
}