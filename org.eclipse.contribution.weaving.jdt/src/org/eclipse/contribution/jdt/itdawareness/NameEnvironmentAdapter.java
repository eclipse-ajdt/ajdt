/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.contribution.jdt.itdawareness;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.PlatformObject;

/**
 * @author Andrew Eisenberg
 * @created Dec 15, 2009
 *
 */
public class NameEnvironmentAdapter extends PlatformObject implements IAdaptable {

    private static final NameEnvironmentAdapter INSTANCE = new NameEnvironmentAdapter();
    
    public static NameEnvironmentAdapter getInstance() {
        return INSTANCE;
    }
    
    private NameEnvironmentAdapter() { }

    private INameEnvironmentProvider provider = null;
    
    public INameEnvironmentProvider getProvider() {
        if (provider == null) {
            provider = (INameEnvironmentProvider) getAdapter(INameEnvironmentProvider.class);
        }
        return provider;
    }
    
    @SuppressWarnings("unchecked")
    public Object getAdapter(Class adapter) {
        if (adapter == INameEnvironmentProvider.class) {
            return super.getAdapter(adapter);
        }
        return null;
    }
    
    /*
     * Not API!  for testing only
     */
    public void setProvider(INameEnvironmentProvider provider) {
        this.provider = provider;
    }
}
