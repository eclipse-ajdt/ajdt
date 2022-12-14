/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.internal.javamodel;

import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.part.FileEditorInput;

/**
 * Moved the UI parts of AJCompilationUnitManager here
 * @author mchapman
 */
public class AJCompilationUnitUtils {

	public static void removeCUsfromJavaModelAndCloseEditors(IProject project) {
		List removed = AJCompilationUnitManager.INSTANCE
				.removeCUsfromJavaModel(project);
		Iterator iter = removed.iterator();
		while (iter.hasNext()) {
			closeEditorForFile((IFile) iter.next());
		}
	}

	protected static void removeFileFromModelAndCloseEditors(IFile file) {
		AJCompilationUnitManager.INSTANCE.removeFileFromModel(file);
		closeEditorForFile(file);
	}

	private static void closeEditorForFile(IFile file) {
		IWorkbenchPage page = JavaPlugin.getActivePage();
		if (page != null) {
			IEditorPart part = page.findEditor(new FileEditorInput(file));
			if (part != null)
				if (!page.closeEditor(part, true))
					//in case user cancels closeEditor, we should not
					// remove unit from model
					//TODO: maybe throw exception (?)
					return;
		}
	}
}