/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.tests.performance;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * @since 3.1
 */
public class PerformanceTestSuite extends TestSuite {

	public static Test suite() {
		return new PerformanceTestSetup(new PerformanceTestSuite());
	}
	
	public PerformanceTestSuite() {
		// add new performance tests here:
		
		
		addTest(new OpenJavaEditorTest.Setup(EmptyTestCase.suite(), false)); // the actual test runs in its own workbench (see test.xml)
		addTest(new OpenAspectJEditorTest.Setup(EmptyTestCase.suite(), false)); // the actual test runs in its own workbench (see test.xml)
	}
}
