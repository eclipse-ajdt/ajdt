/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Sian January - initial version
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.visualiser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.contribution.visualiser.VisualiserPlugin;
import org.eclipse.contribution.visualiser.core.ProviderManager;
import org.eclipse.contribution.visualiser.interfaces.IGroup;
import org.eclipse.contribution.visualiser.interfaces.IMember;
import org.eclipse.contribution.visualiser.jdtImpl.JDTContentProvider;
import org.eclipse.contribution.visualiser.jdtImpl.JDTGroup;
import org.eclipse.contribution.visualiser.jdtImpl.JDTMember;
import org.eclipse.contribution.visualiser.simpleImpl.SimpleMember;
import org.eclipse.contribution.visualiser.utils.JDTUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPart;

/**
 * AJDT Content Provider for the Visualiser.
 */
public class AJDTContentProvider extends JDTContentProvider {

	List currentGroups;

	List currentMembers;

	// Access this variable via its getter method, which handles initialisation
	private List includedFiles;

	/**
	 * Get all the groups to display.
	 * 
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getAllGroups()
	 */
	public List getAllGroups() {
		if (currentGroups == null) {
			updateData();
		} 

		return currentGroups;
	}

	/**
	 * Get all the members to display.
	 * 
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getAllMembers()
	 */
	public List getAllMembers() {
		if (currentMembers == null) {
			updateData();
		} 
			
		return currentMembers;
	}

	/**
	 * Keeps the currentJavaElement and currentProject information up to date in
	 * this class, as this method is called whenever a user changes their
	 * selection in the workspace.
	 */
	public void selectionChanged(IWorkbenchPart workbenchPart, ISelection selection) {

		/*
		 * What is the significance of this check?
		 * 
		 * 'If we are not the current content provider, we don't need to bother
		 * updating to reflect the new selection'?
		 * 
		 * Yes, but what does it meant to be the current content provider? It's
		 * not analagous to being the curerntly selected perspective.
		 * 
		 * Also, this code still gets called, even when the Visualiser
		 * perspective has been closed. Guess it's still loaded.
		 * 
		 * -spyoung
		 */

		if (!(ProviderManager.getContentProvider().equals(this))) {
			return;
		}

		boolean updateRequired = false;
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			Object firstElement = structuredSelection.getFirstElement();

			if (firstElement != null) {
				if (firstElement instanceof IJavaElement) {
					IJavaElement javaElement = (IJavaElement) firstElement;
					if (currentlySelectedJE == javaElement) {
						return;
					}
					currentlySelectedJE = javaElement;
					updateRequired = true;
					if (javaElement.getJavaProject() != null) {
						setCurrentProject(javaElement.getJavaProject());
					}
				}
			}
		}

		if (updateRequired) {
			reset();
			VisualiserPlugin.refresh();
		}
	}

	/**
	 * Get the data for the current selection.
	 * 
	 * @param members -
	 *            add members to the List being returned if true, otherwise add
	 *            groups
	 * @return data
	 */
	private void updateData() {

		if (ProviderManager.getMarkupProvider() instanceof AJDTMarkupProvider) {
			((AJDTMarkupProvider) ProviderManager.getMarkupProvider()).resetMarkupsAndKinds();
		}
		long stime = System.currentTimeMillis();
		List newGroups = new ArrayList();
		List newMembers = new ArrayList();
		if (currentProject != null) {

			try {
				if (currentlySelectedJE instanceof IJavaProject) {
					// Process contents of a Java project
					IPackageFragment[] packageFragments = ((IJavaProject) currentlySelectedJE).getPackageFragments();
					for (int i = 0; i < packageFragments.length; i++) {
						if (!(packageFragments[i].isReadOnly())) {
							addMembersAndGroups(newGroups, newMembers, packageFragments[i]);
						}
					}
				} else if (currentlySelectedJE instanceof IPackageFragment) {
					// Process contents of a Java package(fragment)
					IPackageFragment packageFragment = (IPackageFragment) currentlySelectedJE;
					addMembersAndGroups(newGroups, newMembers, packageFragment);
				} else if (currentlySelectedJE instanceof ICompilationUnit) {
					// Process individually selected compilation units
					JDTMember member = null;

					if (getIncludedFiles(currentProject.getProject()).contains(currentlySelectedJE.getResource())) {
						String memberName = currentlySelectedJE.getElementName();
						if (memberName.endsWith(".java")) { //$NON-NLS-1$
							memberName = memberName.substring(0, memberName.length() - 5);
						} else if (memberName.endsWith(".aj")) { //$NON-NLS-1$
							memberName = memberName.substring(0, memberName.length() - 3);
						}
						member = new JDTMember(memberName, currentlySelectedJE);
						member.setSize(getLength((ICompilationUnit) currentlySelectedJE));
						newMembers.add(member);
					}
					if (member != null) {
						IPackageFragment packageFrag = (IPackageFragment) ((ICompilationUnit) currentlySelectedJE)
								.getParent();
						boolean defaultPackage = packageFrag.isDefaultPackage();
						// ?!? Need to confirm a group for the pkg frag is OK in
						// the case of a selection like thiss
						IGroup group = new JDTGroup(packageFrag.getElementName());
						if (defaultPackage) {
							group.setName("(default package)"); //$NON-NLS-1$
							group.setTooltip("(default package)"); //$NON-NLS-1$
						}
						if (defaultPackage) {
							member.setFullName(member.getName());
						}
						group.add(member);
						newGroups.add(group);
					}
				}

			} catch (JavaModelException jme) {
			}
			long etime = System.currentTimeMillis();

			AJLog.log("AJDTContentProvider.updateData() executed - took " + (etime - stime) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		currentMembers = newMembers;
		currentGroups = newGroups;
	}

	private void addMembersAndGroups(List newGroups, List newMembers, IPackageFragment packageFragment) {
		List classes = getMembersForPackage(packageFragment);
		if (classes.size() > 0) {
			boolean defaultPackage = packageFragment.isDefaultPackage();
			IGroup group = new JDTGroup(packageFragment.getElementName());
			if (defaultPackage) {
				group.setName("(default package)"); //$NON-NLS-1$
				group.setTooltip("(default package)"); //$NON-NLS-1$
			}

			for (Iterator iter = classes.iterator(); iter.hasNext();) {
				Object tempObject = iter.next();
				JDTMember jdtMember = (JDTMember) tempObject;
				group.add(jdtMember);
				newMembers.add(jdtMember);
				if (defaultPackage) {
					((SimpleMember) jdtMember).setFullName(jdtMember.getName());
				}
			}
			newGroups.add(group);
		}
	}

	/**
	 * Override super to reset the colour list when the project changes.
	 * 
	 * @see org.eclipse.contribution.visualiser.jdtImpl.JDTContentProvider#setCurrentProject(org.eclipse.jdt.core.IJavaProject)
	 */
	protected void setCurrentProject(IJavaProject newProject) {
		if (currentProject == null || !currentProject.equals(newProject)) {
			if (ProviderManager.getMarkupProvider() instanceof AJDTMarkupProvider) {
				((AJDTMarkupProvider) ProviderManager.getMarkupProvider()).resetColours();
			}
			currentProject = newProject;
		}
	}

	/**
	 * Clear current cached project/package/file selections.
	 */
	public void reset() {
		currentGroups = null;
		currentMembers = null;

		// clear currently included files
		includedFiles = null;
	}

	/**
	 * Process a mouse click on a member
	 * 
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#processMouseclick(IMember,
	 *      boolean, int)
	 */
	public boolean processMouseclick(IMember member, boolean markupWasClicked, int buttonClicked) {

		if (buttonClicked != 1) {
			return true;
		}
		if (markupWasClicked) {
			return false;
		}
		if (member instanceof JDTMember) {
			IJavaElement jEl = ((JDTMember) member).getResource();
			if (jEl != null) {
				JDTUtils.openInEditor(jEl.getResource(), JDTUtils.getClassDeclLineNum(jEl));
			}
		}

		return false;
	}

	/**
	 * Get all JDT members for the given IPackageFragment (Java package)
	 * 
	 * @param packageFragment
	 * @return List of JDTMembers
	 */
	public List getMembersForPackage(IPackageFragment packageFragment) {
		List returningClasses = new ArrayList();
		try {
			if (containsUsefulStuff(packageFragment)) {
				IJavaElement[] javaElements = packageFragment.getChildren();
				for (int j = 0; j < javaElements.length; j++) {
					if (javaElements[j].getElementType() == IJavaElement.COMPILATION_UNIT) {
						if (getIncludedFiles(packageFragment.getJavaProject().getProject()).contains(
								javaElements[j].getResource())) {

							// Bug 157776: Filter out duplicate compilation units here, not in calling methods
							if (!(javaElements[j] instanceof AJCompilationUnit)
									&& javaElements[j].getElementName() != null
									&& javaElements[j].getElementName().endsWith(".aj")) { //$NON-NLS-1$
								// Do nothing
							} else {
								String memberName = javaElements[j].getElementName();

								if (memberName.endsWith(".java")) { //$NON-NLS-1$
									memberName = memberName.substring(0, memberName.length() - 5);
								} else if (memberName.endsWith(".aj")) { //$NON-NLS-1$
									memberName = memberName.substring(0, memberName.length() - 3);
								}
								JDTMember member = new JDTMember(memberName, javaElements[j]);
								member.setSize(getLength((ICompilationUnit) javaElements[j]));

								returningClasses.add(member);
							}
						}
					}
				}
			}
		} catch (JavaModelException jme) {
		}
		return returningClasses;
	}

	/*
	 * Lazy initialisation method added to avoid null pointer problem(s!) which
	 * were occurring due to assumed state of includedFiles variable which was
	 * then compounded by the assumed state of the currentProject variable (!).
	 * 
	 * -spyoung
	 */
	private List getIncludedFiles(IProject project) {

		if (includedFiles == null) {
			includedFiles = BuildConfig.getIncludedSourceFiles(project);
		}

		return includedFiles;
	}
}
