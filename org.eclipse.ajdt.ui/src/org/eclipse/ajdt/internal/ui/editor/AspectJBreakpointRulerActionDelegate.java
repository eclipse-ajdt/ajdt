/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp. and others.
All rights reserved. This program and the accompanying materials
are made available under the terms of the Eclipse Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/epl-v10.html

Contributors:
    IBM Corporation - Initial implementation
**********************************************************************/
package org.eclipse.ajdt.internal.ui.editor;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.core.resources.IResource;
import org.eclipse.debug.ui.actions.RulerToggleBreakpointActionDelegate;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.texteditor.AbstractRulerActionDelegate;
import org.eclipse.ui.texteditor.ITextEditor;

public class AspectJBreakpointRulerActionDelegate extends RulerToggleBreakpointActionDelegate {

	private IEditorPart fEditorPart;
	/**
	 * @see IEditorActionDelegate#setActiveEditor(bIAction, IEditorPart)
	 */
	public void setActiveEditor(IAction callerAction, IEditorPart targetEditor) {
		// only care about compilation unit and class file editors
		fEditorPart = targetEditor;
		if (targetEditor != null) {
			String id= targetEditor.getSite().getId();
//			System.err.println("ID="+id);
//			System.err.println("JavaUI.ID_CU_EDITOR="+JavaUI.ID_CU_EDITOR);
//			System.err.println("JavaUI.ID_CF_EDITOR="+JavaUI.ID_CF_EDITOR);

// ASC - This is a copy of the JDT internal class, but the if() has been
// extended to allow for our AJDT CompilationUnitEditor - this CUE is
// the same as the ID_CU_EDITOR but for Aspects, to ensure breakpoints
// are handled the same by the AJDT editor as they are by the ID_CU_EDITOR
// we have to ensure the targetEditor below is not nulled out.
			if (!id.equals(JavaUI.ID_CU_EDITOR) && !id.equals(JavaUI.ID_CF_EDITOR) 
			&& 
			!id.equals(AspectJEditor.ASPECTJ_EDITOR_ID))
				targetEditor= null;
		}
		super.setActiveEditor(callerAction, targetEditor);
	}

	
	/**
	 * @see AbstractRulerActionDelegate#createAction()
	 */
	protected IAction createAction(ITextEditor editor,
			IVerticalRulerInfo rulerInfo) {
		IResource resource;
		IEditorInput editorInput = editor.getEditorInput();
		if (editorInput instanceof IFileEditorInput) {
			resource = ((IFileEditorInput) editorInput).getFile();
			if (AspectJPlugin.isAJProject(resource.getProject())) {
				//it's an AspectJ Project, use our action
				return new AspectJBreakpointRulerAction(rulerInfo, editor,
						fEditorPart);
			}
		}
		//	else: use jdts action
		return super.createAction(editor, rulerInfo);
	}

}
