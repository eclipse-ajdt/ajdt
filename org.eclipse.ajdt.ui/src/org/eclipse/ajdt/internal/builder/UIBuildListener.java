/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *     Helen Hawkins - updated for new ajde interface (bug 148190)
 *******************************************************************************/
package org.eclipse.ajdt.internal.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.builder.IAJBuildListener;
import org.eclipse.ajdt.core.builder.IAJCompilerMonitor;
import org.eclipse.ajdt.core.lazystart.IAdviceChangedListener;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerConfiguration;
import org.eclipse.ajdt.internal.ui.ajde.UIMessageHandler;
import org.eclipse.ajdt.internal.ui.markers.DeleteAndUpdateAJMarkersJob;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.contribution.visualiser.VisualiserPlugin;
import org.eclipse.contribution.visualiser.core.ProviderManager;
import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;

/**
 * 
 */
public class UIBuildListener implements IAJBuildListener {

	/**
	 * Map of projects with the IClasspathEntry corresponding
	 * to their outjar
	 */
	private HashMap outjars = null;

	private ListenerList fListeners = new ListenerList();

	/* (non-Javadoc)
	 * @see org.eclipse.ajdt.core.builder.AJBuildListener#preAJBuild(org.eclipse.core.resources.IProject)
	 */
	public void preAJBuild(int kind, IProject project, IProject[] requiredProjects) {		
		// checking to see if the current project has been marked as needing
		// a required project to be rebuilt.
		boolean haveClearedMarkers = false;
		for (int i = 0; i < requiredProjects.length; i++) {
			String referencedMessage = NLS.bind(UIMessages.buildPrereqsMessage, 
					requiredProjects[i].getName());

			if (projectAlreadyMarked(project, referencedMessage)) {
				if (kind == IncrementalProjectBuilder.FULL_BUILD) {
					AJDTUtils.clearProjectMarkers(project, true);
					UIMessageHandler.clearOtherProjectMarkers(project);
				} else {
					AJDTUtils.clearProjectMarkers(project, false);
				}
				markProject(project, referencedMessage);
				haveClearedMarkers = true;
			}
		}
		if (!(haveClearedMarkers)) {
			if (kind == IncrementalProjectBuilder.FULL_BUILD) {
				AJDTUtils.clearProjectMarkers(project, true);
			} else {
				AJDTUtils.clearProjectMarkers(project, false);
			}
			UIMessageHandler.clearOtherProjectMarkers(project);
		}
	}

	
	/*
	 * Check the inpath out folder to see if it exists.
	 * If not, notify the user that the default out folder will be used instead
	 * The out folder may not exist if the project has been renamed and the
	 * outfolder not updated after that.
	 */
	private void checkInpathOutFolder(IProject project) {
	    String outFolder = AspectJCorePreferences.getProjectInpathOutFolder(project);
        if (outFolder == null || outFolder.equals("")) { //$NON-NLS-1$
            // using default outfolder
            return;
        }

	    if (!pathExists(outFolder)) { 
	        try {
	            IMarker errorMarker = project.createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
	            errorMarker.setAttribute(IMarker.MESSAGE, UIMessages.UIBuildListener_InvalidInpathOutFolderText + outFolder);
	            errorMarker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
	            errorMarker.setAttribute(IMarker.LOCATION, "Inpath"); //$NON-NLS-1$
	        } catch (CoreException e) {
	            AJLog.log(AJLog.BUILDER,"build: Problem occured creating the error marker for project " //$NON-NLS-1$
	                            + project.getName() + ": " + e.getStackTrace()); //$NON-NLS-1$
	        }
	    }
    }

    /**
	 * Only want to mark referencing projects once, therefore need to check
	 * whether they've been marked already. Also, if a project has been marked
	 * dont want to build it until its prerequisites have been rebuilt.
	 */
	private boolean projectAlreadyMarked(IProject project, String errorMessage) {
		try {
			IMarker[] problemMarkers = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
					false, IResource.DEPTH_INFINITE);
			if (problemMarkers.length > 0) {
				for (int j = 0; j < problemMarkers.length; j++) {
					IMarker marker = problemMarkers[j];
					int markerSeverity = marker.getAttribute(IMarker.SEVERITY,
							-1);
					String markerMessage = marker.getAttribute(IMarker.MESSAGE,
							"no message"); //$NON-NLS-1$
					if (markerSeverity == IMarker.SEVERITY_ERROR
							&& markerMessage.equals(errorMessage)) {
						return true;
					}
				}
			}
		} catch (CoreException e) {
			AJLog.log(AJLog.BUILDER,"build: Problem occured finding the markers for project " //$NON-NLS-1$
							+ project.getName() + ": " + e.getStackTrace()); //$NON-NLS-1$
		}
		return false;
	}
	
	private void markProject(IProject project, String errorMessage) {
		try {
			IMarker errorMarker = project.createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
			errorMarker.setAttribute(IMarker.MESSAGE, errorMessage);
			errorMarker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
		} catch (CoreException e) {
			AJLog.log(AJLog.BUILDER,"build: Problem occured creating the error marker for project " //$NON-NLS-1$
							+ project.getName() + ": " + e.getStackTrace()); //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ajdt.core.builder.AJBuildListener#postAJBuild(org.eclipse.core.resources.IProject)
	 */
	public void postAJBuild(int kind, final IProject project, /*boolean buildCancelled,*/ boolean noSourceChanges) {
		if (noSourceChanges) {
			return;
		}
		
		// The message to feature in the problems view of depending projects
		String buildPrereqsMessage = NLS.bind(UIMessages.buildPrereqsMessage,
				project.getName());
		boolean buildCancelled = ((IAJCompilerMonitor)AspectJPlugin.getDefault().
				getCompilerFactory().getCompilerForProject(project).getBuildProgressMonitor()).
				buildWasCancelled();
		if (buildCancelled) {
			markReferencingProjects(project, buildPrereqsMessage);
		} else {
			removeMarkerOnReferencingProjects(project, buildPrereqsMessage);
		}

		// Bug22258: Get the compiler monitor to display any issues with
		// that compile.
		((UIMessageHandler)AspectJPlugin.getDefault().getCompilerFactory()
				.getCompilerForProject(project).getMessageHandler()).showOutstandingProblems(project);

		// before returning, check to see if the project sent its output
		// to an outjar and if so, then update any depending projects
		checkOutJarEntry(project);
		
		checkInpathOutFolder(project);
		
		
		// update the markers on files, but only the ones that have changed
		DeleteAndUpdateAJMarkersJob deleteUpdateMarkers;
		CoreCompilerConfiguration compilerConfig = getCompilerConfiguration(project);
		switch (kind) {
		    case IncrementalProjectBuilder.CLEAN_BUILD:
		        deleteUpdateMarkers = new DeleteAndUpdateAJMarkersJob(project);
		        deleteUpdateMarkers.doDeleteOnly(true);
		        deleteUpdateMarkers.schedule();
		        break;
		        
		    case IncrementalProjectBuilder.FULL_BUILD:
		        deleteUpdateMarkers = new DeleteAndUpdateAJMarkersJob(project);
		        deleteUpdateMarkers.schedule();
		        break;
		        
            case IncrementalProjectBuilder.AUTO_BUILD:
            case IncrementalProjectBuilder.INCREMENTAL_BUILD:
                File[] touchedFiles = compilerConfig.getChangedFiles();
                if (touchedFiles == null /* recreate all markers */ || 
                        touchedFiles.length > 0) {
                    
                    deleteUpdateMarkers = new DeleteAndUpdateAJMarkersJob(project, touchedFiles);
                    deleteUpdateMarkers.schedule();
                }
		}
		
		if (AspectJUIPlugin.getDefault().getDisplay().isDisposed()) {
			AJLog.log("Not updating vis, xref, or changes views as display is disposed!"); //$NON-NLS-1$
		} else {
			AspectJUIPlugin.getDefault().getDisplay().asyncExec(
				new Runnable() {
					public void run() {
				        AJLog.logStart("Post compile");

				        // TODO: can we determine whether there were
						// actually changes to the set of advised elements?
						Object[] listeners= fListeners.getListeners();
						for (int i= 0; i < listeners.length; i++) {
							((IAdviceChangedListener) listeners[i]).adviceChanged();
						}

    					// refresh Cross References
    					if (AspectJUIPlugin.usingXref) {
    						XReferenceUIPlugin.refresh();
    					}

    					// refresh Visualiser
    					if (AspectJUIPlugin.usingVisualiser) {
    						Bundle vis = Platform
    								.getBundle(AspectJUIPlugin.VISUALISER_ID);
    						// avoid activating the bundle if it's not active already
    						if ((vis != null) && (vis.getState() == Bundle.ACTIVE)) {
    							if (ProviderManager.getContentProvider() instanceof AJDTContentProvider) {
    								AJDTContentProvider provider = (AJDTContentProvider) ProviderManager
    										.getContentProvider();
    								provider.reset();
    								VisualiserPlugin.refresh();
    							}
    						}
    					}
    			        AJLog.logEnd(AJLog.BUILDER, "Post compile");
    				}
    			});
		}
	}


    public void postAJClean(IProject project) {
        DeleteAndUpdateAJMarkersJob job = new DeleteAndUpdateAJMarkersJob(project);
        job.doDeleteOnly(true);
        job.schedule();
    }


    private CoreCompilerConfiguration getCompilerConfiguration(
            final IProject project) {
        return  (CoreCompilerConfiguration)
                AspectJPlugin.getDefault().getCompilerFactory()
                .getCompilerForProject(project).getCompilerConfiguration();
    }

	public void addAdviceListener(IAdviceChangedListener adviceListener) {
		fListeners.add(adviceListener);
	}

	public void removeAdviceListener(IAdviceChangedListener adviceListener) {
		fListeners.remove(adviceListener);
	}

	/**
	 * If the build has been aborted then mark any referencing projects with a
	 * marker saying so
	 */
	private void markReferencingProjects(IProject project, String errorMessage) {
		IProject[] referencingProjects = getDependingProjects(project);
		for (int i = 0; i < referencingProjects.length; i++) {
			IProject referencingProject = referencingProjects[i];
			if (!(projectAlreadyMarked(referencingProject, errorMessage))) {
				markProject(referencingProject, errorMessage);
			}
		}
	}

	/**
	 * The build wasn't cancelled therefore need to remove any markers on
	 * referencing projects indicating that the current project needs to be
	 * built
	 */
	private void removeMarkerOnReferencingProjects(IProject project,
			String errorMessage) {
		try {
			IProject[] referencingProjects = getDependingProjects(project);
			for (int i = 0; i < referencingProjects.length; i++) {
				IProject referencingProject = referencingProjects[i];
				IMarker[] problemMarkers = referencingProject.findMarkers(
						IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
						false, IResource.DEPTH_INFINITE);
				if (problemMarkers.length > 0) {
					for (int j = 0; j < problemMarkers.length; j++) {
						IMarker marker = problemMarkers[j];
						int markerSeverity = marker.getAttribute(
								IMarker.SEVERITY, -1);
						String markerMessage = marker.getAttribute(
								IMarker.MESSAGE, "no message"); //$NON-NLS-1$

						if (markerSeverity == IMarker.SEVERITY_ERROR
								&& markerMessage.equals(errorMessage)) {
							marker.delete();
						}
					}
				}
			}
		} catch (CoreException e) {
			AJLog.log(AJLog.BUILDER,"build: Problem occured either finding the markers for project " //$NON-NLS-1$
							+ project.getName()
							+ ", or deleting the error marker: " //$NON-NLS-1$
							+ e.getStackTrace());
		}
	}

	/**
	 * If a project has specified an outjar then update the classpath of
	 * depending projects to include this outjar (unless the classpath already
	 * contains it). If the project hasn't specified an outjar then check
	 * whether it did last time it was built. In this case, remove the oujar
	 * from the classpath of depending projects. 
	 */
	private void checkOutJarEntry(IProject project) {
		String outJar = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration().getOutJar();
		if (outJar != null && !(outJar.equals(""))) {  //$NON-NLS-1$
			if (outjars == null) {
				outjars = new HashMap();
			}
			IPath newPath = getRelativePath(project, outJar);
			IClasspathEntry newEntry = JavaCore.newLibraryEntry(newPath
					.makeAbsolute(), null, null);
			if (outjars.containsKey(project))  {
				if (!(outjars.get(project).equals(newEntry))) {
					IClasspathEntry oldEntry = (IClasspathEntry)outjars.get(project);
					outjars.remove(project);
					removeOutjarFromDependingProjects(project,oldEntry);
					outjars.put(project,newEntry);
					updateDependingProjectsWithJar(project,newEntry);
				}				
			} else {
				outjars.put(project,newEntry);
				updateDependingProjectsWithJar(project,newEntry);					
			}
		} else {
			if (outjars != null && outjars.containsKey(project)) {
				IClasspathEntry oldEntry = (IClasspathEntry)outjars.get(project);
				outjars.remove(project);
				if (outjars.size() == 0) {
					outjars = null;
				}
				removeOutjarFromDependingProjects(project, oldEntry);
			}
		}
	}

	private void removeOutjarFromDependingProjects(IProject project,
			IClasspathEntry unwantedEntry) {
		IProject[] dependingProjects = getDependingProjects(project);

		for (int i = 0; i < dependingProjects.length; i++) {
			IJavaProject javaProject = JavaCore.create(dependingProjects[i]);
			if (javaProject == null)
				continue;
			try {
				IClasspathEntry[] cpEntry = javaProject.getRawClasspath();
				List newEntries = new ArrayList();
				for (int j = 0; j < cpEntry.length; j++) {
					if(!cpEntry[j].equals(unwantedEntry)) {
						newEntries.add(cpEntry[j]);
					}
				}
				IClasspathEntry[] newCP = (IClasspathEntry[]) newEntries
						.toArray(new IClasspathEntry[newEntries.size()]);
				javaProject.setRawClasspath(newCP, new NullProgressMonitor());
			} catch (CoreException e) {
			}
		}
	}

	private void updateDependingProjectsWithJar(IProject project, IClasspathEntry newEntry) {
		IProject[] dependingProjects = getDependingProjects(project);
		
		goThroughProjects: for (int i = 0; i < dependingProjects.length; i++) {
			IJavaProject javaProject = JavaCore.create(dependingProjects[i]);
			if (javaProject == null)
				continue;
			try {
				IClasspathEntry[] cpEntry = javaProject.getRawClasspath();
				List newEntries = new ArrayList();
				for (int j = 0; j < cpEntry.length; j++) {
					if(cpEntry[j].equals(newEntry)) {
						continue goThroughProjects;
					} else {
						newEntries.add(cpEntry[j]);
					}
				}
			    newEntries.add(newEntry);
				IClasspathEntry[] newCP = (IClasspathEntry[]) newEntries
						.toArray(new IClasspathEntry[newEntries.size()]);
				javaProject.setRawClasspath(newCP, new NullProgressMonitor());
			} catch (CoreException e) {
			}
		}
	}

	/**
	 * Get all the projects that depend on this project. This includes both
	 * project and class folder dependencies.
	 */
	private IProject[] getDependingProjects(IProject project) {
		IProject[] referencingProjects = project.getReferencingProjects();
		// this only gets the class folder depending projects
		IProject[] classFolderReferences = (IProject[]) CoreUtils
				.getDependingProjects(project).get(0);
		IProject[] dependingProjects = new IProject[referencingProjects.length
				+ classFolderReferences.length];
		for (int i = 0; i < referencingProjects.length; i++) {
			dependingProjects[i] = referencingProjects[i];
		}
		for (int i = 0; i < classFolderReferences.length; i++) {
			dependingProjects[i + referencingProjects.length] = classFolderReferences[i];
		}
		return dependingProjects;
	}

	private IPath getRelativePath(IProject project, String outJar) {
		StringBuffer sb = new StringBuffer(outJar);
		int index = sb.lastIndexOf(project.getName());
		IPath path;
		if (index > 0) {
			path = new Path(sb.substring(sb.lastIndexOf(project.getName())));
		} else {
			path = new Path(outJar);
		}
		return path.makeAbsolute();
	}

    private boolean pathExists(String pathStr) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IPath path = new Path(pathStr);
        if (path.segmentCount() == 1) {
            // bug 153682: getFolder fails when the path is a project
            return root.findMember(path).exists();
        } else {
            return root.getFolder(path).exists();
        }   
    }
	
}
