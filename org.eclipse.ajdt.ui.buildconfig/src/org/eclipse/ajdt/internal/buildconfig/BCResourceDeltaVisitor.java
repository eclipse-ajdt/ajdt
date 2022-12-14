/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Luzius Meisser - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.buildconfig;

import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.internal.bc.BuildConfiguration;
import org.eclipse.ajdt.ui.buildconfig.DefaultBuildConfigurator;
import org.eclipse.ajdt.ui.buildconfig.IBuildConfigurator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;

/**
 * @author Luzius Meisser
 * 
 * This ResourceDeltaVisitor initiates the appropriate measures if it finds
 * removed folders or changes in project descriptions.
 * 
 * If a folder gets deleted, BuildConfigurations that used this folder as source
 * need to be changed. If a project description changes, we have to let the
 * BuildConfigurator check if its aj nature was removed.
 *  
 */
public class BCResourceDeltaVisitor implements IResourceDeltaVisitor {
	private IBuildConfigurator myBCor;
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent)
	 */
	public BCResourceDeltaVisitor() {
		myBCor = DefaultBuildConfigurator.getBuildConfigurator();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
	 */
	public boolean visit(IResourceDelta delta) {
		IResource myRes = delta.getResource();
		if (myRes.getType() == IResource.FILE) {
			switch (delta.getKind()) {
			case IResourceDelta.CHANGED:
				if (BuildConfiguration.EXTENSION.equals(myRes
						.getFileExtension())) {
					new UpdateJob(myBCor, UpdateJob.BUILD_CONFIG_CHANGED, myRes)
							.schedule();
				} else if (".classpath".equals(myRes.getName())) { //$NON-NLS-1$
					new UpdateJob(myBCor, UpdateJob.CLASSPATH_CHANGED, myRes)
							.schedule();
				}
				break;
			case IResourceDelta.REMOVED:
				//Build Configuration has been deleted
				if (BuildConfiguration.EXTENSION.equals(myRes
						.getFileExtension())) {
					new UpdateJob(myBCor, UpdateJob.BUILD_CONFIG_REMOVED, myRes)
							.schedule();
				}
				break;
			case IResourceDelta.ADDED:
				if ((delta.getFlags() & IResourceDelta.MOVED_FROM) == 0){
					if (CoreUtils.ASPECTJ_SOURCE_FILTER.accept(myRes
							.getName())) {
						new UpdateJob(myBCor, UpdateJob.SOURCE_ADDED, myRes)
								.schedule();
					}
				} else {
					IFile oldLocation = myRes.getWorkspace().getRoot().getFile(delta.getMovedFromPath());
					if (oldLocation != null){
						new UpdateJob(myBCor, UpdateJob.FILE_MOVED, myRes, oldLocation).schedule();
					}
				}
				break;
			}
		}
		return true;
	}
}