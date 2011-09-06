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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 */
package org.forgerock.openidm.shell.felixgogo;

import org.apache.felix.service.command.CommandSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Abstract class of Felix GoGo Commands Service
 *
 * @author $author$
 * @version $Revision$ $Date$
 */
public class AbstractFelixCommandsService {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractFelixCommandsService.class);

    private Object service;

    public AbstractFelixCommandsService(Object service) {
        this.service = service;
    }

    /**
     * Run command method
     *
     * @param commandName command name
     * @param params      command parameters
     */
    protected void runCommand(String commandName, CommandSession session, String[] params) {
        try {
            Method method = service.getClass().getMethod(commandName, PrintWriter.class, String[].class);
            PrintWriter printWriter = new PrintWriter(session.getConsole());
            method.invoke(service, printWriter, params);
            printWriter.flush();
        } catch (NoSuchMethodException e) {
            session.getConsole().println("No such command: " + commandName);
        } catch (Exception e) {
            logger.warn("Unable to execute command: {} with args: {}", new Object[]{commandName, Arrays.toString(params)}, e);
            e.printStackTrace(session.getConsole());
        }
    }

}

