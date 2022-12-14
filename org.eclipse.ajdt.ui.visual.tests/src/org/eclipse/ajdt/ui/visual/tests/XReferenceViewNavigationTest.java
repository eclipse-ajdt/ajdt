/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.ajdt.ui.visual.tests;

import java.util.ArrayList;

import org.eclipse.ajdt.core.javaelements.AdviceElement;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.contribution.xref.core.XReferenceAdapter;
import org.eclipse.contribution.xref.internal.ui.actions.ToggleShowXRefsForFileAction;
import org.eclipse.contribution.xref.internal.ui.providers.TreeObject;
import org.eclipse.contribution.xref.internal.ui.providers.TreeParent;
import org.eclipse.contribution.xref.ui.views.XReferenceView;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.ITextEditor;


public class XReferenceViewNavigationTest extends VisualTestCase {

	protected void setUp() throws Exception {	
		super.setUp();
		// set defaults
		IPreferenceStore pstore = AspectJUIPlugin.getDefault()
			.getPreferenceStore();
		pstore.setValue(AspectJPreferences.XREF_CHECKED_FILTERS, ""); //$NON-NLS-1$
		pstore.setValue(AspectJPreferences.XREF_CHECKED_FILTERS_INPLACE, ""); //$NON-NLS-1$
	}
	
	/**
	 * Tests the use of the "Show xrefs for entire file" button,
	 * note that have to call the run method directly on the action
	 * after it's been checked.
	 */
	public void testShowXRefsForEntireFile() throws Exception {
		
		// import the Bean example and check that the xref view
		// is showing
		IProject project = createPredefinedProject("Bean Example"); //$NON-NLS-1$
		IViewPart view = AspectJUIPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow()
			.getActivePage().findView(XReferenceView.ID);
		if (view == null || !(view instanceof XReferenceView)) {
			fail("xrefView should be showing"); //$NON-NLS-1$
		}		
		final XReferenceView xrefView = (XReferenceView)view;
		
		// choose to show the xrefs for the entire file
		final ToggleShowXRefsForFileAction a = (ToggleShowXRefsForFileAction)xrefView.getToggleShowXRefsForEntireFileAction();
		a.setChecked(true);
		a.run();

		// open BoundPoint.aj and select the pointcut
		IResource res = project.findMember("src/bean/BoundPoint.aj"); //$NON-NLS-1$
		if (res == null || !(res instanceof IFile)) {
			fail("src/bean/BoundPoint.aj file not found."); //$NON-NLS-1$
		} 
		IFile ajFile = (IFile)res;				
		final ITextEditor editorPart = (ITextEditor)openFileInAspectJEditor(ajFile, false);
		editorPart.setFocus();
		gotoLine(24);
		waitForJobsToComplete();
		
		// wait for the xref view to contain something
		XRefVisualTestUtils.waitForXRefViewToContainSomething();

		// get the contents of the xref view and check that it has
		// one reference source with 8 different cross references
		ArrayList originalContents = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
		assertEquals("there should be one reference source featured in the xref view",1,originalContents.size()); //$NON-NLS-1$
		TreeParent originalParentNode = XRefVisualTestUtils.getTopLevelNodeInXRefView(xrefView,(XReferenceAdapter)originalContents.get(0));
		assertEquals("There should be 8 cross references shown",8,originalParentNode.getChildren().length); //$NON-NLS-1$
		
		// click somewhere else in the file where if "show xrefs for entire
		// file" wasn't selected, would change the contents of the view
		gotoLine(70);
		moveCursorRight(4);
		waitForJobsToComplete();
		
		// wait for the xref view to receive the selection event
		new DisplayHelper(){
			protected boolean condition() {
				return ((TextSelection)xrefView.getLastSelection()).getOffset() == 2308;
			}
		}.waitForCondition(display, 5000);
		
		// wait for the xref view to contain something
		XRefVisualTestUtils.waitForXRefViewToContainSomething();
		
		// check that we have the same contents in the xref view
		final ArrayList newContents1 = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
		assertEquals("there should be one reference source featured in the xref view",1,newContents1.size()); //$NON-NLS-1$
		assertEquals("xref view should contain the same contents",originalContents,newContents1); //$NON-NLS-1$
		
		TreeParent newParentNode1 = XRefVisualTestUtils.getTopLevelNodeInXRefView(xrefView,(XReferenceAdapter)newContents1.get(0));
		assertEquals("There should be 8 cross references shown",8,newParentNode1.getChildren().length); //$NON-NLS-1$
		assertEquals("xref view should contain the same top level node",originalParentNode,newParentNode1); //$NON-NLS-1$
		
		// turn off "show xrefs for entire file. The xref view should
		// show xrefs for the current selection (piece of around advice)
		a.setChecked(false);
		a.run();
		waitForJobsToComplete();
		// wait for the toggle to be turned off
		new DisplayHelper() {

			protected boolean condition() {
				return !(a.isChecked());
			}
		
		}.waitForCondition(Display.getCurrent(), 5000);
		assertFalse("Should not be showing xrefs for entire file",a.isChecked());																																																																
		// wait for the xref view to be populated with the current selection
		new DisplayHelper() {

			protected boolean condition() {
				ArrayList currentContents = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
				if (currentContents.size() > 0) {
					TreeParent tp = XRefVisualTestUtils.getTopLevelNodeInXRefView(xrefView,(XReferenceAdapter)currentContents.get(0));
					return (tp.getData() instanceof AdviceElement);
				}
				return (currentContents != null && !currentContents.equals(newContents1));
			}
		
		}.waitForCondition(Display.getCurrent(), 5000);

		// check that the view only contains the xrefs for the around advice
		final ArrayList newContents2 = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
		assertEquals("there should be one reference source featured in the xref view",1,newContents2.size()); //$NON-NLS-1$
				
		TreeParent newParentNode2 = XRefVisualTestUtils.getTopLevelNodeInXRefView(xrefView,(XReferenceAdapter)newContents2.get(0));
		assertTrue("top level node in xref view should be an AdviceElement",(newParentNode2.getData() instanceof AdviceElement)); //$NON-NLS-1$
		assertEquals("There should be 1 cross references shown",1,newParentNode2.getChildren().length); //$NON-NLS-1$
		
		AdviceElement ae = (AdviceElement)newParentNode2.getData();
		assertEquals("the name of the current parent node should be around","around" ,ae.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$
	
		// turn "show xrefs for entire file" back on and the view should now
		// contain the xrefs for the entire file
		a.setChecked(true);
		a.run();
		
		// wait for the xref view to be populated with the current selection
		new DisplayHelper() {

			protected boolean condition() {
				ArrayList currentContents = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
				boolean ret = (currentContents != null && !currentContents.equals(newContents2));
				return ret;
			}
		
		}.waitForCondition(Display.getCurrent(), 5000);
		
		// check that the view now shows the xrefs for the entire file
		ArrayList newContents3 = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
		assertEquals("there should be one reference source featured in the xref view",1,newContents3.size()); //$NON-NLS-1$
		TreeParent newParentNode3 = XRefVisualTestUtils.getTopLevelNodeInXRefView(xrefView,(XReferenceAdapter)newContents3.get(0));
		assertEquals("There should be 8 cross references shown",8,newParentNode3.getChildren().length); //$NON-NLS-1$
		
		TreeObject[] originalChildren = originalParentNode.getChildren();
		TreeObject[] newChildren = newParentNode3.getChildren();
		
			
		for (int i = 0; i < newChildren.length; i++) {
			JavaElement child = (JavaElement)newChildren[i].getData();	
			boolean foundMatch = false;
			for (int j = 0; j < originalChildren.length; j++) {
				JavaElement oldChild = (JavaElement)originalChildren[j].getData();
				if (oldChild.equals(child)) {
					foundMatch = true;
				}
			}
			assertTrue("the new tree of xrefs should contain the same xrefs" + //$NON-NLS-1$
					" as the old tree", foundMatch); //$NON-NLS-1$
		}
		
		// navigate to one of the advised places shown in the xref
		// view and press return to navigate to the corresponding place
		// in the editor (should open up a new editor)
		AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().activate(xrefView);
		waitForJobsToComplete();

		// press "down" four times and then return (navigates to something
		// which advises something in Demo.java
		postKey(SWT.ARROW_DOWN);
		postKey(SWT.ARROW_DOWN);
		postKey(SWT.ARROW_DOWN);
		postKey(SWT.ARROW_DOWN);
		postKey(SWT.CR);
		
		// wait for the new editor to be opened
		new DisplayHelper() {

			protected boolean condition() {
				IEditorPart activeEditor = AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
				boolean ret = (activeEditor != null && !activeEditor.equals(editorPart) && 
						(activeEditor.getEditorInput().getName().indexOf("Demo.java") != -1));
				return ret;
			}
		
		}.waitForCondition(Display.getCurrent(), 5000);
		
		Object o = xrefView.getTreeViewer().getInput();
		waitForJobsToComplete();
		final IEditorPart newEditor = AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		assertTrue("Demo.java should have been opened in the editor but found "
				+ newEditor.getEditorInput().getName(),
				(newEditor.getEditorInput().getName().indexOf("Demo.java") != -1));  //$NON-NLS-1$

		// wait for the xref view to contain something
		//XRefVisualTestUtils.waitForXRefViewToContainSomething();
		XRefVisualTestUtils.waitForXRefViewToContainSomethingNew(o);
		
		// get the cross references and check that the view 
		// is showing the xrefs for the entire file
		final ArrayList newContents4 = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
		assertEquals("there should be one reference source featured in the xref view",1,newContents4.size()); //$NON-NLS-1$
		TreeParent newParentNode4 = XRefVisualTestUtils.getTopLevelNodeInXRefView(xrefView,(XReferenceAdapter)newContents4.get(0));
		assertEquals("There should be 1 cross reference shown",1,newParentNode4.getChildren().length); //$NON-NLS-1$
		assertEquals("the root node in the xref view should be entitled Demo","Demo",((JavaElement)newParentNode4.getData()).getElementName()); //$NON-NLS-1$ //$NON-NLS-2$
		
		// turn off "show xrefs for entire file" 
		a.setChecked(false);
		a.run();
		waitForJobsToComplete();
		
		// navigate to one of the advised places shown in the xref
		// view and press return to navigate to the corresponding place
		// in the editor (should open up a new editor)
		AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().activate(xrefView);
		waitForJobsToComplete();
		IWorkbenchPart activePart = AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().getActivePart();
		assertTrue("xref view should be the active part",activePart instanceof XReferenceView);
		
		// press "down" three times and then return
		postKey(SWT.ARROW_DOWN);
		postKey(SWT.ARROW_DOWN);
		postKey(SWT.ARROW_DOWN);
		postKey(SWT.CR);
		
		// wait for the new editor to be opened
		new DisplayHelper() {

			protected boolean condition() {
				IEditorPart activeEditor = AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
				boolean ret = (activeEditor != null && !activeEditor.equals(newEditor));
				return ret;
			}
		
		}.waitForCondition(Display.getCurrent(), 5000);
		
		IEditorPart newEditor2 = AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		assertTrue("BoundPoing.aj should have been opened in the editor",(newEditor2.equals(editorPart)));  //$NON-NLS-1$
		
		// wait for the xref view to be populated with the current selection
		new DisplayHelper() {

			protected boolean condition() {
				ArrayList currentContents = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
				boolean ret = (currentContents != null && !currentContents.equals(newContents4));
				return ret;
			}
		
		}.waitForCondition(Display.getCurrent(), 5000);
		
		// get the cross references and check that the view 
		// is showing the xrefs for the around advice only
		ArrayList newContents5 = XRefVisualTestUtils.getContentsOfXRefView(xrefView);
		assertEquals("there should be one reference source featured in the xref view",1,newContents5.size()); //$NON-NLS-1$
		TreeParent newParentNode5 = XRefVisualTestUtils.getTopLevelNodeInXRefView(xrefView,(XReferenceAdapter)newContents5.get(0));
		assertEquals("There should be 1 cross reference shown",1,newParentNode5.getChildren().length); //$NON-NLS-1$
		assertEquals("the root node in the xref view should be entitled around","around",((JavaElement)newParentNode5.getData()).getElementName()); //$NON-NLS-1$ //$NON-NLS-2$

	}
	
}
