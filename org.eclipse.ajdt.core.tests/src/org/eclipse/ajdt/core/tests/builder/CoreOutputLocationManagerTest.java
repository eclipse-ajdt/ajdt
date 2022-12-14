/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation 
 * 				 Matt Chapman   - initial version
 *               Helen Hawkins - updated for new ajde interface (bug 148190)
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.builder;

import java.io.File;

import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.internal.core.ajde.CoreOutputLocationManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

public class CoreOutputLocationManagerTest extends AJDTCoreTestCase {

	public void testOutputLocationManager() throws Exception {
		IProject project = createPredefinedProject("MultipleOutputFolders"); //$NON-NLS-1$
		CoreOutputLocationManager om = new CoreOutputLocationManager(project);
		IFile class1 = (IFile) project.findMember("src/p1/Class1.java"); //$NON-NLS-1$
		File file1 = class1.getLocation().toFile();
		File out1 = om.getOutputLocationForClass(file1);
		assertTrue("Output location for " + class1 //$NON-NLS-1$
				+ " should end in bin. Got: " + out1, out1.toString() //$NON-NLS-1$
				.endsWith("bin")); //$NON-NLS-1$

		IFile class2 = (IFile) project.findMember("src2/p2/Class2.java"); //$NON-NLS-1$
		File file2 = class2.getLocation().toFile();
		File out2 = om.getOutputLocationForClass(file2);
		assertTrue("Output location for " + class2 //$NON-NLS-1$
				+ " should end in bin2. Got: " + out2, out2.toString() //$NON-NLS-1$
				.endsWith("bin2")); //$NON-NLS-1$

		IFile class3 = (IFile) project.findMember("src2/p2/GetInfo.aj"); //$NON-NLS-1$
		File file3 = class3.getLocation().toFile();
		File out3 = om.getOutputLocationForClass(file3);
		assertTrue("Output location for " + class3 //$NON-NLS-1$
				+ " should end in bin2. Got: " + out3, out3.toString() //$NON-NLS-1$
				.endsWith("bin2")); //$NON-NLS-1$
	}

	public void testOutputLocationManagerBug153682() throws Exception {
		IProject project = createPredefinedProject("bug153682"); //$NON-NLS-1$
		CoreOutputLocationManager om = new CoreOutputLocationManager(project);
		IFile class1 = (IFile) project.findMember("foo/Test.java"); //$NON-NLS-1$
		File file1 = class1.getLocation().toFile();
		File out1 = om.getOutputLocationForClass(file1);
		assertTrue("Output location for " + class1 //$NON-NLS-1$
				+ " should end in bin. Got: " + out1, out1.toString() //$NON-NLS-1$
				.endsWith("bin")); //$NON-NLS-1$

		IFile class2 = (IFile) project.findMember("foo/test.properties"); //$NON-NLS-1$
		File file2 = class2.getLocation().toFile();
		File out2 = om.getOutputLocationForResource(file2);
		assertTrue("Output location for " + class2 //$NON-NLS-1$
				+ " should end in bin. Got: " + out2, out2.toString() //$NON-NLS-1$
				.endsWith("bin")); //$NON-NLS-1$
	}
	
	public void testOutputLocationManagerBug160846() throws Exception {
		IProject project = createPredefinedProject("bug160846"); //$NON-NLS-1$
		CoreOutputLocationManager om = new CoreOutputLocationManager(project);
		IFile class1 = (IFile) project.findMember("src/java/org/noco/aj/MainClass.java"); //$NON-NLS-1$
		File file1 = class1.getLocation().toFile();
		File out1 = om.getOutputLocationForClass(file1);
		assertTrue("Output location for " + class1 //$NON-NLS-1$
				+ " should end in classes. Got: " + out1, out1.toString() //$NON-NLS-1$
				.endsWith("classes")); //$NON-NLS-1$

		IFile class2 = (IFile) project.findMember("test/java/org/noco/aj/MainClassTest.java"); //$NON-NLS-1$
		File file2 = class2.getLocation().toFile();
		File out2 = om.getOutputLocationForResource(file2);
		assertTrue("Output location for " + class2 //$NON-NLS-1$
				+ " should end in test-classes. Got: " + out2, out2.toString() //$NON-NLS-1$
				.endsWith("test-classes")); //$NON-NLS-1$
	}
	
	public void testInpathOutLocation() throws Exception {
	    IProject project1 = createPredefinedProject("ExportAsJar"); //$NON-NLS-1$
	    IProject project2 = createPredefinedProject("JarOnInpath"); //$NON-NLS-1$
	    CoreOutputLocationManager om = new CoreOutputLocationManager(project2);
	    IFile class1 = (IFile) project1.findMember("export.jar"); //$NON-NLS-1$
	    File file1 = class1.getLocation().toFile();
	    File out1 = om.getOutputLocationForClass(file1);
        assertTrue("Output location for " + class1 //$NON-NLS-1$
                + " should end in InpathOut. Got: " + out1, out1.toString() //$NON-NLS-1$
                .endsWith("InpathOut")); //$NON-NLS-1$
	}
}
