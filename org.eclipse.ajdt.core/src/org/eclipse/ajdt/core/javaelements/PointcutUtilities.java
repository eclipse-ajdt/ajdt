/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman  - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core.javaelements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public class PointcutUtilities {

	/**
	 * Returns the index of the first occurrence of the given character in the
	 * source string, between the start offset and the limit. Returns -1 if the
	 * character is not found in the defined range.
	 * 
	 * @param source
	 * @param offset
	 * @param limit
	 * @param c
	 * @return
	 */
	public static int findNextChar(String source, int offset, int limit, char c) {
		while (source.charAt(offset) != c) {
			offset++;
			if ((offset == limit) || (offset == source.length())) {
				return -1;
			}
		}
		return offset;
	}

	/**
	 * Selects the identifier at the given offset
	 * 
	 * @param source
	 * @param offset
	 * @return
	 */
	public static String findIdentifier(String source, int offset) {
		int start = offset - 1;
		while ((start > 0)
				&& (Character.isJavaIdentifierStart(source.charAt(start)) || source
						.charAt(start) == '.')) {
			start--;
		}
		if (start == offset) {
			return ""; //$NON-NLS-1$
		}
		int end = offset;
		while ((end < source.length())
				&& (Character.isJavaIdentifierPart(source.charAt(end)) || source
						.charAt(end) == '.')) {
			end++;
		}
		String s = source.substring(start + 1, end);
		return s;
	}
	
	// returns a map of id strings to a list of offsets
	public static Map findAllIdentifiers(String source) {
		int pos = findNextChar(source, 0, source.length()-1, ':');
		if (pos == -1) {
			pos = 0;
		}
		
		boolean lookingForStart = true;
		boolean done = false;
		int start = 0;
		Map idMap = new HashMap();
		for (int i = pos+1; !done && i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') {
				done = true;
			} else if (lookingForStart) {
				if (Character.isJavaIdentifierStart(c)) {
					start = i;
					lookingForStart = false;
				}
			} else {
				if (!Character.isJavaIdentifierPart(c)) {
					String id = source.substring(start,i);
					if (!isAjPointcutKeyword(id)) {
						List offsetList = (List)idMap.get(id);
						if (offsetList==null) {
							offsetList = new ArrayList();
							idMap.put(id, offsetList);
						}
						offsetList.add(new Integer(i));
					}
					lookingForStart = true;
				}
			}
		}
		return idMap;
	}
	
	/**
	 * Given an AspectJ compilation unit and an offset, determine whether that
	 * offset occurs within the definition of a pointcut, or the pointcut
	 * section of an advice element. If so, the full source of the compilation
	 * unit is required, otherwise null is returned.
	 * 
	 * @param ajcu
	 * @param offset
	 * @return
	 */
	public static String isInPointcutContext(AJCompilationUnit ajcu, int offset) {
		try {
			IJavaElement el = ajcu.getElementAt(offset);
			if ((el instanceof AdviceElement)
					|| (el instanceof PointcutElement)) {
				// now narrow down to after the colon and before
				// the start of the advice body or the end of the pointcut
				ajcu.requestOriginalContentMode();
				String source = ((ISourceReference) ajcu).getSource();
				ajcu.discardOriginalContentMode();
				ISourceRange range = ((ISourceReference) el).getSourceRange();
				int start = range.getOffset();
				int end = start + range.getLength();
				int colon = PointcutUtilities.findNextChar(source, start, end,
						':');
				// we need to be after a colon
				if ((colon != -1) && (offset > colon)) {
					int openBrace = PointcutUtilities.findNextChar(source,
							colon, end, '{');
					int endZone = openBrace;
					if (endZone == -1) {
						int semiColon = PointcutUtilities.findNextChar(source,
								colon, end, ';');
						endZone = semiColon;
					}
					// we need to be before the end zone
					if ((endZone > 0) && (offset < endZone)) {
						return source;
					}
				}
			}
		} catch (JavaModelException e) {
		}
		return null;
	}

	/**
	 * Return a pointcut in the given aspect with the given aspect, if there is
	 * one, otherwise return null/
	 * 
	 * @param aspect
	 * @param name
	 * @return
	 * @throws JavaModelException
	 */
	public static PointcutElement findPointcutInAspect(AspectElement aspect,
			String name) throws JavaModelException {
		PointcutElement[] pointcuts = aspect.getPointcuts();
		for (int i = 0; i < pointcuts.length; i++) {
			if (name.equals(pointcuts[i].getElementName())) {
				return pointcuts[i];
			}
		}
		return null;
	}

	/**
	 * Given a java element representing a pointcut or advice, and a name,
	 * attempt to resolve that name as a pointcut refered to by the given
	 * pointcut or advice. If a matching pointcut is not resolved, null is
	 * returned.
	 * 
	 * @param el
	 * @param name
	 * @return
	 * @throws JavaModelException
	 */
	public static IJavaElement findPointcut(IJavaElement el, String name)
			throws JavaModelException {
		IJavaElement element = null;
		IJavaElement parent = el.getParent();
		if (parent instanceof AspectElement) {
			AspectElement aspect = (AspectElement) parent;

			// handle a pointcut in another type
			if (name.indexOf('.') > 0) {
				int ind = name.lastIndexOf('.');
				String typeName = name.substring(0, ind);
				String pcName = name.substring(ind + 1);
				String[][] res = aspect.resolveType(typeName);
				if ((res != null) && (res.length > 0)) {
					IType type = aspect.getJavaProject().findType(
							res[0][0] + "." + res[0][1]); //$NON-NLS-1$
					if (type != null) {
						IMethod[] methods = type.getMethods();
						for (int i = 0; i < methods.length; i++) {
							if (pcName.equals(methods[i].getElementName())) {
								// make sure the method is really a pointcut
								if ("Qpointcut;".equals(methods[i] //$NON-NLS-1$
										.getReturnType())) {
									return methods[i];
								}
							}
						}
					}
				}
			}

			// see if the pointcut is in the same aspect
			PointcutElement pc = PointcutUtilities.findPointcutInAspect(aspect,
					name);
			if (pc != null) {
				return pc;
			}

			// next, see if the pointcut is inherited from an abstract aspect
			String superName = aspect.getSuperclassName();
			if ((superName != null) && (superName.length() > 0)) {
				List cus = AJCompilationUnitManager.INSTANCE.getCachedCUs(aspect.getJavaProject().getProject());
				for (Iterator iter = cus.iterator(); iter.hasNext();) {
					AJCompilationUnit ajcu = (AJCompilationUnit) iter.next();
					IType[] types = ajcu.getTypes();
					for (int i = 0; i < types.length; i++) {
						if (types[i].getElementName().equals(superName)) {
							if (types[i] instanceof AspectElement) {
								pc = PointcutUtilities.findPointcutInAspect(
										(AspectElement) types[i], name);
								if (pc != null) {
									return pc;
								}
							}
						}
					}
				}
			}

			// the name might refer to a regular class
			String[][] res = aspect.resolveType(name);
			if ((res != null) && (res.length > 0)) {
				IType type = aspect.getJavaProject().findType(
						res[0][0] + "." + res[0][1]); //$NON-NLS-1$
				if (type != null) {
					return type;
				}
			}
		}
		return element;
	}

	/**
	 * Utility method which returns true if the given string is a keyword that
	 * can appear in a pointcut definition.
	 * 
	 * @param word
	 * @return
	 */
	public static boolean isAjPointcutKeyword(String word) {
		for (int i = 0; i < AspectJPlugin.ajKeywords.length; i++) {
			if (AspectJPlugin.ajKeywords[i].equals(word)) {
				return true;
			}
		}
		// "this" and "if" are not in the aj list as they are java keywords
		if ("this".equals(word)) { //$NON-NLS-1$
			return true;
		}
		if ("if".equals(word)) { //$NON-NLS-1$
			return true;
		}
		return false;
	}

}