/*******************************************************************************
 * Copyright (c) 2008 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.javaelements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.SourceTypeElementInfo;

/**
 * This class provides element info for SourceTypes that know about 
 * which inter-type declarations are declared on them.
 * 
 * @author andrew
 */
public class ITDAwareSourceTypeInfo extends SourceTypeElementInfo {
    
    public ITDAwareSourceTypeInfo(SourceType type) throws JavaModelException {
        this((ISourceType) type.getElementInfo(), type);
    }

    public ITDAwareSourceTypeInfo(ISourceType toCopy, SourceType type) {
        this.handle = new SourceType((JavaElement) type.getParent(), type.getElementName()) {
            public Object getElementInfo() throws JavaModelException {
                return ITDAwareSourceTypeInfo.this;
            }
        };

        this.setFlags(toCopy.getModifiers());
        this.setSuperclassName(toCopy.getSuperclassName());
        this.setSuperInterfaceNames(toCopy.getInterfaceNames());
        this.setNameSourceEnd(toCopy.getNameSourceEnd());
        this.setNameSourceStart(toCopy.getNameSourceStart());
        this.setSourceRangeEnd(toCopy.getDeclarationSourceEnd());
        this.setSourceRangeStart(toCopy.getDeclarationSourceStart());
        
        this.setChildren(augmentChildren(type));

        // still some more fields, but left unset
    } 
    
    
    private IJavaElement[] augmentChildren(SourceType type) {
        try {
            IJavaElement[] origChildren = type.getChildren();
            // recur through original children
            // ensure that children are also ITD aware
            for (int i = 0; i < origChildren.length; i++) {
                if (origChildren[i].getElementType() == IJavaElement.TYPE) {
                    final SourceType innerType = (SourceType) origChildren[i];
                    final ITDAwareSourceTypeInfo innerInfo = new ITDAwareSourceTypeInfo(innerType);
                    origChildren[i] = 
                            new SourceType(type, 
                                    innerType.getElementName()) {
                        public Object getElementInfo() throws JavaModelException {
                            return innerInfo;
                        }
                    };                                
                }
            }
            
            
            List itdChildren = initializeITDs(type);
            if (itdChildren.size() > 0 ) {
                IJavaElement[] allChildren = new IJavaElement[origChildren.length + itdChildren.size()];
                System.arraycopy(origChildren, 0, allChildren, 0, origChildren.length);
                int i = origChildren.length;
                for (Iterator childIter = itdChildren.iterator(); childIter.hasNext();) {
                    IJavaElement elt = (IJavaElement) childIter.next();
                    allChildren[i++] = elt;
                }
                return allChildren;
            } else {
                return origChildren;
            }
        } catch (JavaModelException e) {
            return null;
        }
    }
    
    private List/*IJavaElement*/ initializeITDs(SourceType type) {
        AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(type);
        if (model.hasModel()) {
            try {
                List/*IJavaElement*/ itds = new ArrayList();
                List/*IJavaElement*/ rels = model.getRelationshipsForElement(type, AJRelationshipManager.ASPECT_DECLARATIONS);
                for (Iterator relIter = rels.iterator(); relIter.hasNext();) {
                    IJavaElement ije = (IJavaElement) relIter.next();
                    if (ije instanceof IntertypeElement) {
                        IntertypeElement elt = (IntertypeElement) ije;
                        IMember member = elt.createMockDeclaration(type);
                        // null if the ITD doesn't exist in the AspectJ hierarchy
                        // will happen if the Java side has partial compilation 
                        // and aspectj sode does not
                        if (member != null) { 
                            switch (member.getElementType()) {
                            case IJavaElement.METHOD:
                                if (((IMethod) member).isConstructor()) {
                                    itds.add(member);
                                } else {
                                    itds.add(member);
                                }
                                break;
                            case IJavaElement.FIELD:
                                itds.add(member);
                                break;
                            }
                        }
                    } else if (ije instanceof DeclareElement) {
                        DeclareElement elt = (DeclareElement) ije;
                        
                        // use createElementInfo, not getElementInfo because the element info doesn't seem to be created properly
                        // XXX in the future, change back to using getElementInfo for efficiency
                        DeclareElementInfo info = (DeclareElementInfo) elt.createElementInfo();
                        if (info.isExtends()) {
                            this.setSuperclassName(info.getType());
                        } else if (info.isImplements()) {
                            char[][] origInterfaces = this.getInterfaceNames();
                            char[][] itdInterfaces = info.getTypes();
                            char[][] newInterfaces;
                            if (origInterfaces == null) {
                                newInterfaces = itdInterfaces;
                            } else if (itdInterfaces == null) {
                                newInterfaces = origInterfaces;
                            } else {
                                newInterfaces = new char[origInterfaces.length + info.getTypes().length][];
                                System.arraycopy(origInterfaces, 0, newInterfaces, 0, origInterfaces.length);
                                System.arraycopy(itdInterfaces, 0, newInterfaces, origInterfaces.length, itdInterfaces.length);
                            }
                            setSuperInterfaceNames(newInterfaces);
                        }
                    }
                }
                return itds;
            } catch (JavaModelException e) {
            }
        } 
        return Collections.EMPTY_LIST;
    }
    
    
}
