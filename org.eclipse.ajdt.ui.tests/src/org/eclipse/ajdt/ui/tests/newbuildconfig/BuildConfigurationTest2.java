/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January    - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.newbuildconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;

import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.internal.ui.refactoring.ReaderInputStream;
import org.eclipse.ajdt.ui.buildpath.BuildConfigurationUtils;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

public class BuildConfigurationTest2 extends UITestCase {

	
	public void testExcludeAllWithNoPackages() throws Exception {
		IProject project = createPredefinedProject("WithoutSourceFolder"); //$NON-NLS-1$		
		waitForJobsToComplete();	
		checkIncluded(project, 2);
		IFile propertiesFile = project.getFile("none.ajproperties"); //$NON-NLS-1$
		propertiesFile.create(new ReaderInputStream(new StringReader("src.includes = /\n" + //$NON-NLS-1$
				"src.excludes = A.aj,\\\n" +  //$NON-NLS-1$
                "C.java")), true, null); //$NON-NLS-1$
		waitForJobsToComplete();
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		checkIncluded(project, 0);	
		
		IFile newPropertiesFile = project.getFile("none2.ajproperties"); //$NON-NLS-1$
		BuildConfigurationUtils.saveBuildConfiguration(newPropertiesFile);
		newPropertiesFile.refreshLocal(1, null);
		waitForJobsToComplete();
		compareFiles(propertiesFile, newPropertiesFile);
	}
	
	public void testExcludeAllWithPackages() throws Exception {
		IProject project = createPredefinedProject("Figures Demo"); //$NON-NLS-1$		
		waitForJobsToComplete();	
		checkIncluded(project, 13);
		IFile propertiesFile = project.getFile("none.ajproperties"); //$NON-NLS-1$
		propertiesFile.create(new ReaderInputStream(new StringReader("src.includes = /\n" + //$NON-NLS-1$
				"src.excludes = figures/")), true, null); //$NON-NLS-1$
		waitForJobsToComplete();
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		checkIncluded(project, 0);	
		
		IFile newPropertiesFile = project.getFile("none2.ajproperties"); //$NON-NLS-1$
		BuildConfigurationUtils.saveBuildConfiguration(newPropertiesFile);
		newPropertiesFile.refreshLocal(1, null);
		waitForJobsToComplete();
		
		compareFiles(propertiesFile, newPropertiesFile);
	}
	
	public void testExcludeSomeWithPackages() throws Exception {
		IProject project = createPredefinedProject("Figures Demo"); //$NON-NLS-1$		
		waitForJobsToComplete();	
		checkIncluded(project, 13);
		IFile propertiesFile = project.getFile("build.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		checkIncluded(project, 12);	
		
		IFile newPropertiesFile = project.getFile("build2.ajproperties"); //$NON-NLS-1$
		BuildConfigurationUtils.saveBuildConfiguration(newPropertiesFile);
		newPropertiesFile.refreshLocal(1, null);
		waitForJobsToComplete();
		
		compareFiles(propertiesFile, newPropertiesFile);
	}

	private void checkIncluded(IProject project, int numFiles) {
		List included = BuildConfig.getIncludedSourceFiles(project);
		assertEquals(numFiles, included.size());
	}

	private void compareFiles(IFile propertiesFile, IFile newPropertiesFile) throws CoreException, IOException {
		BufferedReader br = null;
		BufferedReader br2 = null;
		try {
			br = new BufferedReader(new InputStreamReader(propertiesFile.getContents()));
			br2 = new BufferedReader(new InputStreamReader(newPropertiesFile.getContents()));
			String line1 = br.readLine();
			String line2 = br2.readLine();
			while(line1 != null && line2 != null) {
				assertEquals(line1.trim(), line2.trim());
				line1 = br.readLine();
				line2 = br2.readLine();				
			}
		} finally {
			if(br != null) {
				br.close();
			}
			if(br2 != null) {
				br2.close();
			}
		}
	}
}
