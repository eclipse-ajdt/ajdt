/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Luzius Meisser - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.codeconversion;

/**
 * A class to manage the different convertion flag configurations.
 * 
 * @author Luzius Meisser
 */
public class ConversionOptions {
	
	// A description of how code completion works in AJDT can be found in bug 74419.
	public static final ConversionOptions CODE_COMPLETION = new ConversionOptions(true, false, true);
	
	public static final ConversionOptions STANDARD = new ConversionOptions(false, true, false);
	public static final ConversionOptions CONSTANT_SIZE = new ConversionOptions(false, false, false);

	private boolean thisJoinPointReferencesEnabled;
	private boolean dummyTypeReferencesForOrganizeImportsEnabled;
	private boolean addAjcTagToIntertypesEnabled;
	private int codeCompletePosition = -1;
	private char[] targetType = null;
	
	/**
	 * @param thisJoinPointReferencesEnabled
	 * @param dummyTypeReferencesForOrganizeImportsEnabled
	 */
	public ConversionOptions(boolean thisJoinPointReferencesEnabled,
			boolean dummyTypeReferencesForOrganizeImportsEnabled, boolean addAjcTagToIntertypesEnabled) {
		this.addAjcTagToIntertypesEnabled = addAjcTagToIntertypesEnabled;
		this.thisJoinPointReferencesEnabled = thisJoinPointReferencesEnabled;
		this.dummyTypeReferencesForOrganizeImportsEnabled = dummyTypeReferencesForOrganizeImportsEnabled;
	}

	public boolean isDummyTypeReferencesForOrganizeImportsEnabled() {
		return dummyTypeReferencesForOrganizeImportsEnabled;
	}
	public boolean isThisJoinPointReferencesEnabled() {
		return thisJoinPointReferencesEnabled;
	}

	public static ConversionOptions getCodeCompletionOptionWithContextSwitch(int position, char[] targetType){
		ConversionOptions opts = new ConversionOptions(true, false, true);
		opts.targetType = targetType;
		opts.codeCompletePosition = position;
		return opts;
	}
	public int getCodeCompletePosition() {
		return codeCompletePosition;
	}
	public char[] getTargetType() {
		return targetType;
	}
	public boolean isAddAjcTagToIntertypesEnabled() {
		return addAjcTagToIntertypesEnabled;
	}
}
