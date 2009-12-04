/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package com.xpn.xwiki.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang.StringUtils;
import org.xwiki.bridge.DocumentNameSerializer;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.logging.AbstractLogEnabled;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.rendering.internal.macro.wikibridge.DefaultWikiMacro;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.macro.wikibridge.WikiMacro;
import org.xwiki.rendering.macro.wikibridge.WikiMacroFactory;
import org.xwiki.rendering.macro.wikibridge.WikiMacroException;
import org.xwiki.rendering.macro.wikibridge.WikiMacroDescriptor;
import org.xwiki.rendering.macro.wikibridge.WikiMacroParameterDescriptor;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * The default implementation of {@link org.xwiki.rendering.macro.wikibridge.WikiMacroFactory}.
 * 
 * @version $Id$
 * @since 2.0M2
 */
@Component
public class DefaultWikiMacroFactory extends AbstractLogEnabled implements WikiMacroFactory, WikiMacroConstants
{
    /**
     * The {@link ComponentManager} component.
     */
    @Requirement
    private ComponentManager componentManager;

    /**
     * The {@link Execution} component used for accessing XWikiContext.
     */
    @Requirement
    private Execution execution;

    /**
     * Used to serialize document names into strings.
     */
    @Requirement
    private DocumentNameSerializer docNameSerializer;

    /**
     * Utility method for accessing XWikiContext.
     * 
     * @return the XWikiContext.
     */
    private XWikiContext getContext()
    {
        return (XWikiContext) this.execution.getContext().getProperty("xwikicontext");
    }

    /**
     * {@inheritDoc}
     */
    public WikiMacro createWikiMacro(String documentName) throws WikiMacroException
    {
        XWikiDocument doc = null;
        try {
            doc = getContext().getWiki().getDocument(documentName, getContext());
        } catch (XWikiException ex) {
            throw new WikiMacroException(String.format(
                "Could not build macro from : [%s], unable to load document", documentName), ex);
        }
        return buildMacro(doc);
    }

    /**
     * Creates a {@link WikiMacro} from an {@link XWikiDocument} which contains a macro definition.
     * 
     * @param doc the {@link XWikiDocument} to look for a macro definition.
     * @return a {@link WikiMacro} found inside the document.
     * @throws org.xwiki.rendering.macro.wikibridge.WikiMacroException invalid macro definition / no macro definition found.
     */
    private WikiMacro buildMacro(XWikiDocument doc) throws WikiMacroException
    {
        String fullDocumentName = docNameSerializer.serialize(doc.getDocumentName());

        // Check whether this document contains a macro definition.
        BaseObject macroDefinition = doc.getObject(WIKI_MACRO_CLASS);
        if (null == macroDefinition) {
            throw new WikiMacroException(String.format("No macro definition found in document : [%s]",
                fullDocumentName));
        }

        // Extract macro definition.
        String macroId = macroDefinition.getStringValue(MACRO_ID_PROPERTY);
        String macroName = macroDefinition.getStringValue(MACRO_NAME_PROPERTY);
        String macroDescription = macroDefinition.getStringValue(MACRO_DESCRIPTION_PROPERTY);
        String macroDefaultCategory = macroDefinition.getStringValue(MACRO_DEFAULT_CATEGORY_PROPERTY);
        boolean macroSupportsInlineMode = (macroDefinition.getIntValue(MACRO_INLINE_PROPERTY) == 0) ? false : true;
        String macroContentType = macroDefinition.getStringValue(MACRO_CONTENT_TYPE_PROPERTY);
        String macroContentDescription = macroDefinition.getStringValue(MACRO_CONTENT_DESCRIPTION_PROPERTY);
        String macroCode = macroDefinition.getStringValue(MACRO_CODE_PROPERTY);

        // Verify macro id.
        if (StringUtils.isEmpty(macroId)) {
            throw new WikiMacroException(String.format(
                "Incomplete macro definition in [%s], macro id is empty", fullDocumentName));
        }

        // Verify macro name.
        if (StringUtils.isEmpty(macroName)) {
            macroName = macroId;
            getLogger().warn(
                String.format("Incomplete macro definition in [%s], macro name is empty", fullDocumentName));
        }

        // Verify macro description.
        if (StringUtils.isEmpty(macroDescription)) {
            getLogger().warn(
                String.format("Incomplete macro definition in [%s], macro description is empty", fullDocumentName));
        }

        // Verify default macro category.
        if (StringUtils.isEmpty(macroDefaultCategory)) {
            macroDefaultCategory = null;
            getLogger()
                .warn(
                    String.format("Incomplete macro definition in [%s], default macro category is empty",
                        fullDocumentName));
        }

        // Verify macro content type.
        if (StringUtils.isEmpty(macroContentType)) {
            macroContentType = MACRO_CONTENT_OPTIONAL;
        }

        // Verify macro content description.
        if (!macroContentType.equals(MACRO_CONTENT_EMPTY) && StringUtils.isEmpty(macroContentDescription)) {
            String errorMsg = "Incomplete macro definition in [%s], macro content description is empty";
            getLogger().warn(String.format(errorMsg, fullDocumentName));
            macroContentDescription = "Macro content";
        }

        // Verify macro code.
        if (StringUtils.isEmpty(macroCode)) {
            throw new WikiMacroException(String.format(
                "Incomplete macro definition in [%s], macro code is empty", fullDocumentName));
        }

        // Extract macro parameters.
        List<WikiMacroParameterDescriptor> parameterDescriptors = new ArrayList<WikiMacroParameterDescriptor>();
        Vector<BaseObject> macroParameters = doc.getObjects(WIKI_MACRO_PARAMETER_CLASS);
        if (null != macroParameters) {
            for (BaseObject macroParameter : macroParameters) {
                // Vectors can contain null values
                if (null == macroParameter) {
                    continue;
                }

                // Extract parameter definition.
                String parameterName = macroParameter.getStringValue(PARAMETER_NAME_PROPERTY);
                String parameterDescription = macroParameter.getStringValue(PARAMETER_DESCRIPTION_PROPERTY);
                boolean parameterMandatory =
                    (macroParameter.getIntValue(PARAMETER_MANDATORY_PROPERTY) == 0) ? false : true;

                // Verify parameter name.
                if (StringUtils.isEmpty(parameterName)) {
                    throw new WikiMacroException(String.format(
                        "Incomplete macro definition in [%s], macro parameter name is empty", fullDocumentName));
                }

                // Verify parameter description.
                if (StringUtils.isEmpty(parameterDescription)) {
                    String errorMessage = "Incomplete macro definition in [%s], macro parameter description is empty";
                    getLogger().warn(String.format(errorMessage, fullDocumentName));
                }

                // Create the parameter descriptor.
                parameterDescriptors.add(new WikiMacroParameterDescriptor(parameterName, parameterDescription,
                    parameterMandatory));
            }
        }

        // Create macro content descriptor.
        ContentDescriptor contentDescriptor = null;
        if (!macroContentType.equals(MACRO_CONTENT_EMPTY)) {
            contentDescriptor =
                new DefaultContentDescriptor(macroContentDescription, macroContentType.equals(MACRO_CONTENT_MANDATORY));
        }

        // Create macro descriptor.
        MacroDescriptor macroDescriptor = new WikiMacroDescriptor(macroName, macroDescription, macroDefaultCategory,
            contentDescriptor, parameterDescriptors);

        // Create & return the macro.
        return new DefaultWikiMacro(fullDocumentName, macroId, macroSupportsInlineMode, macroDescriptor, macroCode,
            doc.getSyntaxId(), componentManager);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsWikiMacro(String documentName)
    {
        boolean result = true;
        try {
            XWikiDocument doc = getContext().getWiki().getDocument(documentName, getContext());
            BaseObject macroDefinition = doc.getObject(WIKI_MACRO_CLASS);
            result = (null != macroDefinition);
        } catch (XWikiException ex) {
            result = false;
        }
        return result;
    }
}
