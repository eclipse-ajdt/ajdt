/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Matt Chapman - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.buildconfig.editor;

import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.pde.internal.ui.editor.PDEFormPage;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.widgets.ScrolledForm;

public class BuildPage extends PDEFormPage {
	public static final String PAGE_ID = "build"; //$NON-NLS-1$
	private BuildContentsSection srcSection;
	
	public BuildPage(FormEditor editor) {
		super(editor, PAGE_ID, UIMessages.BuildPage_name);
	}

	protected void createFormContent(IManagedForm mform) {
		super.createFormContent(mform);
		GridLayout layout = new GridLayout();
		ScrolledForm form = mform.getForm();
		form.setText(UIMessages.AJPropsEditor_BuildPage_title);
		layout.numColumns = 2;
		layout.marginWidth = 10;
		layout.horizontalSpacing = 15;
		layout.verticalSpacing = 10;
		layout.makeColumnsEqualWidth = true;
		form.getBody().setLayout(layout);


		
		srcSection = new SrcSection(this, form.getBody());
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = 2;
		srcSection.getSection().setLayoutData(gd);

		mform.addPart(srcSection);
	}

	
	public void enableAllSections(boolean enable){
		srcSection.enableSection(enable);
	}

}
