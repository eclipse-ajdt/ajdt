/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.visual.tests;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.actions.RenameAction;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class Bug100018Test extends VisualTestCase {

	public void testBug100018() throws Exception {
		// must be a Java project
		IProject project = createPredefinedProject("project.java.Y"); //$NON-NLS-1$
		IFile javaFile = (IFile) project
				.findMember("src/internal/stuff/MyBuilder.java"); //$NON-NLS-1$
		IEditorPart editor = openFileInDefaultEditor(javaFile, true);
		int numEditors = countOpenEditors();
		assertTrue("There should only be one open editor", numEditors == 1); //$NON-NLS-1$

		IJavaProject jp = JavaCore.create(project);
		assertNotNull("Java project is null", jp); //$NON-NLS-1$
		IJavaElement elem = jp.findElement(new Path(
				"internal/stuff/MyBuilder.java"), null); //$NON-NLS-1$
		assertNotNull("Couldn't find IJavaElement for MyBuilder.java", elem); //$NON-NLS-1$

		Runnable r = new Runnable() {
			public void run() {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
				}
				postKey(SWT.ARROW_RIGHT); // deselect hilighted text
				postKey('2'); // new name should now be MyBuilder2
				postKey(SWT.CR);
			}
		};
		new Thread(r).start();

		// create the rename action, and pass it the IJavaElement
		RenameAction rename = new RenameAction(editor.getSite());
		StructuredSelection selection = new StructuredSelection(elem);
		rename.run(selection);

		// bug 100018: the rename operation caused the editor to close
		numEditors = countOpenEditors();
		assertFalse("Bug 100018: Rename operation caused editor to close", //$NON-NLS-1$
				numEditors == 0);
		assertTrue("Wrong number of open editors: expected 1, found " //$NON-NLS-1$
				+ numEditors, numEditors == 1);

	}

	private int countOpenEditors() {
		int count = 0;
		IWorkbenchWindow[] windows = PlatformUI.getWorkbench()
				.getWorkbenchWindows();
		for (int i = 0; i < windows.length; i++) {
			IWorkbenchPage[] pages = windows[i].getPages();
			for (int x = 0; x < pages.length; x++) {
				IEditorReference[] editors = pages[x].getEditorReferences();
				for (int z = 0; z < editors.length; z++) {
					IEditorPart editor = editors[z].getEditor(true);
					if (editor != null) {
						count++;
					}
				}
			}
		}
		return count;
	}
}
