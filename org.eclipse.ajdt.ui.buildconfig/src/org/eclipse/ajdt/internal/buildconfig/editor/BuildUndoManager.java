/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.buildconfig.editor;

import org.eclipse.ajdt.pde.internal.ui.editor.ModelUndoManager;
import org.eclipse.ajdt.pde.internal.ui.editor.PDEFormEditor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.core.IModelChangedEvent;
import org.eclipse.pde.core.build.IBuild;
import org.eclipse.pde.core.build.IBuildEntry;
import org.eclipse.pde.core.build.IBuildModel;
import org.eclipse.pde.internal.core.build.BuildObject;
import org.eclipse.pde.internal.core.build.IBuildObject;

public class BuildUndoManager extends ModelUndoManager {

	public BuildUndoManager(PDEFormEditor editor) {
		super(editor);
		setUndoLevelLimit(30);
	}

	protected String getPageId(Object obj) {
		if (obj instanceof IBuildEntry)
			return BuildPage.PAGE_ID;
		return null;
	}

	protected void execute(IModelChangedEvent event, boolean undo) {
		Object[] elements = event.getChangedObjects();
		int type = event.getChangeType();
		String propertyName = event.getChangedProperty();
		IBuildModel model = (IBuildModel)event.getChangeProvider();

		switch (type) {
			case IModelChangedEvent.INSERT :
				if (undo)
					executeRemove(model, elements);
				else
					executeAdd(model, elements);
				break;
			case IModelChangedEvent.REMOVE :
				if (undo)
					executeAdd(model, elements);
				else
					executeRemove(model, elements);
				break;
			case IModelChangedEvent.CHANGE :
				if (undo)
					executeChange(
						elements[0],
						propertyName,
						event.getNewValue(),
						event.getOldValue());
				else
					executeChange(
						elements[0],
						propertyName,
						event.getOldValue(),
						event.getNewValue());
		}
	}

	private void executeAdd(IBuildModel model, Object[] elements) {
		IBuild build = model.getBuild();

		try {
			for (int i = 0; i < elements.length; i++) {
				Object element = elements[i];

				if (element instanceof IBuildEntry) {
					build.add((IBuildEntry)element);
				}
			}
		} catch (CoreException e) {
		}
	}

	private void executeRemove(IBuildModel model, Object[] elements) {
		IBuild build = model.getBuild();

		try {
			for (int i = 0; i < elements.length; i++) {
				Object element = elements[i];

				if (element instanceof IBuildEntry) {
					build.remove((IBuildEntry)element);
				}
			}
		} catch (CoreException e) {
		}
	}

	private void executeChange(
		Object element,
		String propertyName,
		Object oldValue,
		Object newValue) {
		if (element instanceof BuildObject) {
			BuildObject bobj = (BuildObject) element;
			try {
				bobj.restoreProperty(propertyName, oldValue, newValue);
			} catch (CoreException e) {
			}
		}
	}

	public void modelChanged(IModelChangedEvent event) {
		if (event.getChangeType() == IModelChangedEvent.CHANGE) {
			Object obj = event.getChangedObjects()[0];
			if (obj instanceof IBuildObject) {
				IBuildObject bobj = (IBuildObject) event.getChangedObjects()[0];
				//Ignore events from objects that are not yet in the model.
				if (!(bobj instanceof IBuild) && bobj.isInTheModel() == false)
					return;
			}
		}
		super.modelChanged(event);
	}
}
