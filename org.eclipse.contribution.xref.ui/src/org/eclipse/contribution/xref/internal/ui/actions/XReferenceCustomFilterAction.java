/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ben Dalziel     - initial version
 *******************************************************************************/
package org.eclipse.contribution.xref.internal.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.contribution.xref.core.XReferenceProviderDefinition;
import org.eclipse.contribution.xref.core.XReferenceProviderManager;
import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;
import org.eclipse.contribution.xref.ui.filters.CustomFilterDialog;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.filters.FilterMessages;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Shell;

/**
 * 
 * Class which creates a CustomFilterDialog for the XRef view
 * and handles the returned List.
 * 
 * The difference between XReferenceCustomFilterAction and
 * XReferenceCustomFilterActionInplace is in the view is refreshed
 *
 */
public class XReferenceCustomFilterAction extends Action {

	private List /* XReferenceProviderDefinition */ providerDefns;
	
	private List /* String */ populatingList;
	private List /* String */ checkedList;
	private List /* String */ defaultCheckedList;
	private String dialogTitle;
	private String dialogMessage;
	private Shell parentShell;
	
	public XReferenceCustomFilterAction(Shell shell) {
		
		setText(XReferenceUIPlugin.getResourceString("OpenCustomFiltersDialogAction.text"));
		setImageDescriptor(JavaPluginImages.DESC_ELCL_FILTER);
		setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_FILTER);

		populatingList = new ArrayList();
		checkedList = new ArrayList();
		defaultCheckedList = new ArrayList();
		parentShell = shell;
		
		providerDefns = XReferenceProviderManager.getManager().getRegisteredProviders();
		
		for (Iterator iter = providerDefns.iterator(); iter.hasNext();) {
			XReferenceProviderDefinition provider = (XReferenceProviderDefinition) iter.next();
			
			List providerFilters = provider.getAllFilters();
			if (providerFilters != null) {
				for (Iterator iterator = providerFilters.iterator(); iterator.hasNext();) {
					String filter = (String) iterator.next();
					if (!populatingList.contains(filter)) {
						populatingList.add(filter);						
					}
				}
			}
			
			List providerChecked = provider.getCheckedFilters();
			if (providerChecked != null) {
				for (Iterator iterator = providerChecked.iterator(); iterator.hasNext();) {
					String filter = (String) iterator.next();
					checkedList.add(filter);
				}
			}
			
			List providerDefault = provider.getDefaultFilters();
			if (providerDefault != null) {
				for (Iterator iterator = providerDefault.iterator(); iterator.hasNext();) {
					String filter = (String) iterator.next();
					defaultCheckedList.add(filter);
				}
			}
		}
		dialogTitle = XReferenceUIPlugin.getResourceString("CustomFilterDialog.title"); //$NON-NLS-1$
		dialogMessage = XReferenceUIPlugin.getResourceString("CustomFilterDialog.message"); //$NON-NLS-1$
	}	
	
	public void run() {
		checkedList = CustomFilterDialog.showDialog(parentShell,
				populatingList, checkedList, defaultCheckedList,
				dialogTitle, dialogMessage);

		for (Iterator iter = providerDefns.iterator(); iter.hasNext();) {
			XReferenceProviderDefinition provider = (XReferenceProviderDefinition) iter.next();
			provider.setCheckedFilters(checkedList);
		}
		XReferenceProviderManager.getManager().setIsInplace(false);
		// Refresh XRef View
		XReferenceUIPlugin.refresh();	
	}


	// ----------------- This is for testing ----------------------
	

    /**
     * Returns the List of XReferenceProviderDefinition for the Action - this
     * method is for testing purposes and not part of the
     * published API. 
     */
	public List /* XReferenceProviderDefinition */ getProviderDefns() {
		return providerDefns;
	}
	
    /**
     * Returns the populating List for the Action - this
     * method is for testing purposes and not part of the
     * published API. 
     */
	public List /* String */ getPopulatingList() {
		return populatingList;
	}
}
