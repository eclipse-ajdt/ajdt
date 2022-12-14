/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Luzius Meisser - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.buildconfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.buildconfig.DefaultBuildConfigurator;
import org.eclipse.ajdt.ui.buildconfig.IBuildConfiguration;
import org.eclipse.ajdt.ui.buildconfig.IBuildConfigurator;
import org.eclipse.ajdt.ui.buildconfig.IProjectBuildConfigurator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

/**
 * @author Luzius Meisser
 * 
 * This class manages the Build Configurations of a project. 
 * 
 */
public class ProjectBuildConfigurator implements IProjectBuildConfigurator {
	private HashMap buildconfigs;
	private IJavaProject javaProject;

	IFile activeBuildConfiguration;
	private IBuildConfigurator buildConfigurator;
	private boolean initialized;
	
	public ProjectBuildConfigurator(IJavaProject project) {
		this.javaProject = project;
		buildConfigurator = DefaultBuildConfigurator.getBuildConfigurator();
		buildconfigs = new HashMap();
		init();
	}
	
	public void reInit(){
		initialized = false;
	}
	
	private void init(){
		if (!initialized){
			readBuildConfigurationsFromFileSystem();
			activeBuildConfiguration = getStoredBuildConfiguration();
			initialized = true;
			
			if (buildconfigs.size() > 0) {
				if ((activeBuildConfiguration == null) || !buildconfigs.containsKey(activeBuildConfiguration)) {
					// choose the active configuration
					setActiveBuildConfiguration(getDefaultBuildConfiguration());
				}
			}
		}
	}
	
	private IFile getStoredBuildConfiguration(){
		IProject project = javaProject.getProject();
		String configFile = AspectJPreferences.getActiveBuildConfigurationName(project);
		if ((configFile==null) || configFile.length()==0) {
			return null;
		}
		return project.getFile(configFile);
	}
	
	private void storeActiveBuildConfigurationName(String configName){
		IProject project = javaProject.getProject();
		AspectJPreferences.setActiveBuildConfigurationName(project,configName);
	}

	private void readBuildConfigurationsFromFileSystem() {
		try {
			IResource[] files = javaProject.getProject().members(IResource.FILE);
			for (int i = 0; i < files.length; i++) {
				if ((files[i].getType() != IResource.FOLDER)
						&& BuildConfiguration.EXTENSION.equals(files[i]
								.getFileExtension())
						&& files[i].exists()) {
					BuildConfiguration bc;
					bc = new BuildConfiguration((IFile) files[i], this);
					buildconfigs.put(files[i], bc);
				}
			}
		} catch (CoreException e) {
			//Could not read project members, no BuildConfigurations read
		}
	}

	public IBuildConfiguration getActiveBuildConfiguration() {
		//makeSureThereIsAtLeastOneActiveConfiguration();
		return (BuildConfiguration) buildconfigs.get(activeBuildConfiguration);
	}
	public void setActiveBuildConfiguration(IBuildConfiguration bc) {
		if (buildconfigs.containsKey(bc.getFile())) {
			if(!bc.getFile().exists()) {
				buildconfigs.remove(bc.getFile());
				//makeSureThereIsAtLeastOneActiveConfiguration();
			} else {
				IFile oldActive = activeBuildConfiguration;
				activeBuildConfiguration = bc.getFile();
				storeActiveBuildConfigurationName(bc.getFile().getName());
				bc.update(false);
				if (!activeBuildConfiguration.equals(oldActive)){
					try {
						requestFullBuild(true);
						activeBuildConfiguration.touch(null);
					} catch (CoreException e) {
					}
				}
			}
		}
	}
	public IBuildConfiguration getBuildConfiguration(IFile bcFile) {
		return (BuildConfiguration) buildconfigs.get(bcFile);
	}
	
	private boolean fullbuildrequested;
	
	public void requestFullBuild(boolean temp){
		fullbuildrequested = temp;
	}
	
	public boolean fullBuildRequested(){
		return fullbuildrequested;
	}
	
	//if active buildconfiguration has changed, updated jdt project entries
	//should only be called by resource delta visitor, so if you want to update
	//the build configuration, its better to touch its file than to call this method
	public void configurationChanged(IBuildConfiguration bc) {
		if (initialized){
			if (!buildconfigs.containsKey(bc.getFile())) {
				buildconfigs.put(bc.getFile(), bc);
			}
			if (bc.getFile().equals(activeBuildConfiguration)) {
				
				//why do we need to do full builds after build configuration changes?
				//when doing a normal build, .class files of classes that have been excluded
				//do not get removed from the bin dir so we don't get errors if excluded classes
				//are needed by others.
				requestFullBuild(true);					
				
				//update package explorer view
				AJDTUtils.refreshPackageExplorer();
			}
			((BuildConfigurator)buildConfigurator).notifyChangeListeners();
		}
	}
	
	/**
	 * @return Returns the project.
	 */
	public IJavaProject getJavaProject() {
		return javaProject;
	}
	/**
	 * @param project
	 *            The project to set.
	 */
	public void setProject(IJavaProject project) {
		this.javaProject = project;
	}
	public IFile[] getConfigurationFiles() {
		//makeSureThereIsAtLeastOneActiveConfiguration();
		IFile[] z = new IFile[0];
		return (IFile[]) buildconfigs.keySet().toArray(z);
	}
	/**
	 * @return
	 */
	public Collection getBuildConfigurations() {
		//makeSureThereIsAtLeastOneActiveConfiguration();
		return buildconfigs.values();
	}
	public void addBuildConfiguration(IBuildConfiguration bc) {
		buildconfigs.put(bc.getFile(), bc);
		((BuildConfigurator)buildConfigurator).notifyChangeListeners();
	}

	/**
	 *  
	 */
	private void makeSureThereIsAtLeastOneActiveConfiguration() {
		if (!initialized){
			init();
		}
		if (buildconfigs.size() == 0) {
			BuildConfiguration nbc = new BuildConfiguration(BuildConfiguration.STANDARD_BUILD_CONFIGURATION_NAME, javaProject, this);
			buildconfigs.put(nbc.getFile(), nbc);
		}
		if ((activeBuildConfiguration == null) || !buildconfigs.containsKey(activeBuildConfiguration)) {
			// choose the active configuration
			setActiveBuildConfiguration(getDefaultBuildConfiguration());
		}
	}
	
	/**
	 * If there is no preference setting defining which build config should be
	 * active, we need to pick on from the ones available. Choosing one at
	 * random wouldn't be helpful, so for consistency we define the rule to be:
	 * choose build.ajproperties if there is one with that name, otherwise
	 * choose the first one alphabetically (bug 84310)
	 * 
	 * @return the chosen build configuration
	 */
	private IBuildConfiguration getDefaultBuildConfiguration() {
		if (buildconfigs.size()==1) {
			// hobsons choice
			return (BuildConfiguration)buildconfigs.values().iterator().next();
		}
		IFile first = null;
		for (Iterator iter = buildconfigs.keySet().iterator(); iter.hasNext();) {
			IFile file = (IFile) iter.next();
			if (file.getName().equals(BuildConfiguration.STANDARD_BUILD_CONFIGURATION_FILE)) {
				return (BuildConfiguration)buildconfigs.get(file);
			}
			if (first==null || (file.getName().compareTo(first.getName()) < 0)) {
				first = file;
			}
		}
		return (BuildConfiguration)buildconfigs.get(first);
	}
	
	/**
	 * Deletes the specified build configuration.
	 * If it was the only one, it creates a standard build configuration.
	 * If it was the active one, the next one gets activated.
	 * @param bc Build Configuration to delete
	 */
	public void removeBuildConfiguration(IBuildConfiguration bc){
		if (bc.getFile().equals(activeBuildConfiguration))
			activeBuildConfiguration = null;
		buildconfigs.remove(bc.getFile());
		//makeSureThereIsAtLeastOneActiveConfiguration();
		((BuildConfigurator)buildConfigurator).notifyChangeListeners();
	}

	/**
	 * @param buildFile
	 */
	public void setActiveBuildConfiguration(IFile buildFile) {
		IBuildConfiguration bc = getBuildConfiguration(buildFile);
		if (bc == null){
			bc = new BuildConfiguration(buildFile, this);
			this.addBuildConfiguration(bc);	
		}		
		setActiveBuildConfiguration(bc);
	}

	/**
	 * @param file
	 */
	public void removeBuildConfiguration(IFile file) {
		IBuildConfiguration bc = getBuildConfiguration(file);
		if (bc != null)
			this.removeBuildConfiguration(bc);
	}

	public IBuildConfiguration getActiveBuildConfiguration(boolean create) {
		if (create) {
			makeSureThereIsAtLeastOneActiveConfiguration();
		}
		return getActiveBuildConfiguration();
	}
}