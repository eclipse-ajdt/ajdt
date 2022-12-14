/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - initial version
 *******************************************************************************/

package org.eclipse.ajdt.ui.visual.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllXRefVisualTests {

	public static Test suite() {
		TestSuite suite = new TestSuite(AllXRefVisualTests.class.getName());

		suite.addTest(new TestSuite(XReferenceViewTest.class));
		suite.addTest(new TestSuite(XReferenceViewNavigationTest.class));
		suite.addTest(new TestSuite(XReferenceInplaceDialogTest.class));
		suite.addTest(new TestSuite(XReferenceViewBuildingTest.class));
		suite.addTest(new TestSuite(XReferenceViewAutoOpenTest.class));
		
		return suite;
	}
}
