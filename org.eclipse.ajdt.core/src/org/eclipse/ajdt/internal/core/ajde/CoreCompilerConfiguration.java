/********************************************************************
 * Copyright (c) 2007 Contributors. All rights reserved. 
 * This program and the accompanying materials are made available 
 * under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution and is available at 
 * http://eclipse.org/legal/epl-v10.html 
 *  
 * Contributors: IBM Corporation - initial API and implementation 
 * 				 Helen Hawkins   - initial version (bug 148190)
 *******************************************************************/
package org.eclipse.ajdt.internal.core.ajde;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.aspectj.ajde.core.ICompilerConfiguration;
import org.aspectj.ajde.core.IOutputLocationManager;
import org.aspectj.ajdt.internal.core.builder.CompilerConfigurationChangeFlags;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.text.CoreMessages;
import org.eclipse.ajdt.internal.core.builder.BuildClasspathResolver;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.osgi.util.NLS;

/**
 * ICompilerConfiguration implementation which returns information for all 
 * methods except getNonStandardOptions().  This implementation is used
 * if ajdt.core plugin is not present in the platform
 */
public class CoreCompilerConfiguration implements ICompilerConfiguration {

	private String cachedClasspath = null;
	protected IProject project;
	private CoreOutputLocationManager locationManager;

	// fully qualified list of file names that have been touched since
    // last build
    // set to null originally since we don't know anything 
    // about build state when first created.  Assume everything 
    // has changed.
    private List/* File */ modifiedFiles = null;
    // set of flags describing what has changed since last
    // build
    // initially set to EVERYTHING since we don't know
    // build state when first created.
    private int configurationChanges;
    
    // list of classpath entries that have been rebuilt since last build
    private List /*String*/ classpathElementsWithModifiedContents = null;

    public CoreCompilerConfiguration(IProject project) {
		this.project = project;
		AJLog.log(AJLog.BUILDER, "Compiler configuration for project " + project.getName() + " doesn't know previous state, so assuming EVERYTHING has changed.");
		configurationChanges = EVERYTHING;
	}

	public Map getJavaOptionsMap() {
		Map optionsMap = null;

		JavaProject javaProject;
		try {
			javaProject = (JavaProject) project.getNature(JavaCore.NATURE_ID);
			optionsMap = javaProject.getOptions(true);
		} catch (CoreException e) {
		}

		if (optionsMap == null) {
			return JavaCore.getOptions();
		} else {
			return optionsMap;
		}
	}

	public String getNonStandardOptions() {
		// ajdt.ui supplies impl
		return ""; //$NON-NLS-1$
	}

	public Set getAspectPath() {
		String[] v = AspectJCorePreferences.getResolvedProjectAspectPath(project);

		// need to expand any variables on the path
		String aspectpath = expandVariables(v[0], v[2]);

		// Ensure that every entry in the list is a fully qualified one.
		aspectpath = fullyQualifyPathEntries(aspectpath);

		if (aspectpath.length() == 0)
			return null;

		return mapStringToSet(aspectpath, false);
	}

	public String getClasspath() {
		if (cachedClasspath != null)
			return cachedClasspath;
		IJavaProject jp = JavaCore.create(project);
		// bug 73035: use this build classpath resolver which is a direct
		// copy from JDT, so the classpath environment is much closer between
		// AspectJ and Java projects.
		cachedClasspath = new BuildClasspathResolver().getClasspath(AspectJPlugin.getWorkspace().getRoot(), jp);
		return cachedClasspath;
	}

	public Set getInpath() {
		String[] v = AspectJCorePreferences.getResolvedProjectInpath(project);

		// need to expand any variables on the path
		String inpath = expandVariables(v[0], v[2]);

		// Ensure that every entry in the list is a fully qualified one.
		inpath = fullyQualifyPathEntries(inpath);

		if (inpath.length() == 0)
			return null;

		return mapStringToSet(inpath, false);
	}

	public String getOutJar() {
		String outputJar = AspectJCorePreferences.getProjectOutJar(project);

		// If outputJar does not start with a slash, we might need to prepend
		// the project work directory.
		if (outputJar.trim().length() > 0 && !(outputJar.startsWith("\\") || outputJar.startsWith("/"))) { //$NON-NLS-1$ //$NON-NLS-2$
			String trimmedName = outputJar.trim();
			boolean prependProject = true;

			// It might still be a fully qualified path if the 2nd char is a ':'
			// (i.e. its
			// a windows absolute path with a drive letter in it !)
			if (trimmedName.length() > 1) {
				if (trimmedName.charAt(1) == ':')
					prependProject = false;
			}

			if (prependProject) {
				// Its a relative path, it should be relative to the project.
				String projectBaseDirectory = project.getLocation().toOSString();
				outputJar = new String(projectBaseDirectory + File.separator + outputJar.trim());
			}
		}

		return outputJar;
	}

	public IOutputLocationManager getOutputLocationManager() {
		if (locationManager == null) {
			locationManager = new CoreOutputLocationManager(project);
		}
		return locationManager;
	}

	public List getProjectSourceFiles() {
		List files = BuildConfig.getIncludedSourceFiles(project);
		List iofiles = new ArrayList(files.size());
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			IFile f = (IFile) iter.next();
			iofiles.add(f.getLocation().toOSString());
		}
		return iofiles;
	}

	public Map getSourcePathResources() {
		IJavaProject jProject = JavaCore.create(project);
		Map map = new HashMap();
		try {
			IClasspathEntry[] classpathEntries = jProject.getResolvedClasspath(false);

			// find the absolute output path
			String realOutputLocation;
			IPath workspaceRelativeOutputPath = jProject.getOutputLocation();
			if (workspaceRelativeOutputPath.segmentCount() == 1) { // project
				// root
				realOutputLocation = jProject.getResource().getLocation().toOSString();
			} else {
				IFolder out = ResourcesPlugin.getWorkspace().getRoot().getFolder(workspaceRelativeOutputPath);
				realOutputLocation = out.getLocation().toOSString();
			}
			for (int i = 0; i < classpathEntries.length; i++) {
				if (classpathEntries[i].getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					IClasspathEntry sourceEntry = classpathEntries[i];
					IPath sourcePath = sourceEntry.getPath();
					List files = new ArrayList();
					sourcePath = sourcePath.removeFirstSegments(1);
					IResource[] srcContainer = new IResource[] { project.findMember(sourcePath) };
					if (srcContainer[0] != null) {
						getProjectRelativePaths(srcContainer, files, CoreUtils.RESOURCE_FILTER, srcContainer[0].getFullPath()
								.segmentCount() - 1, sourceEntry);

						ArrayList linkedSrcFolders = getLinkedChildFolders(srcContainer[0]);

						for (Iterator it = files.iterator(); it.hasNext();) {
							String relPath = (String) it.next();
							String fullPath = getResourceFullPath(srcContainer[0], relPath, linkedSrcFolders);

							// put file on list if not in output path
							if (!fullPath.startsWith(realOutputLocation) && !relPath.endsWith(".classpath") //$NON-NLS-1$
									&& !relPath.endsWith(".project") //$NON-NLS-1$
									&& !relPath.endsWith(".ajsym") //$NON-NLS-1$
									&& !relPath.endsWith(".lst")) { //$NON-NLS-1$
								File file = new File(fullPath);
								if (file.exists()) {
									map.put(relPath, file);
								}
							}
						}
					}
				}
			}
		} catch (JavaModelException jmEx) {
			// bug 90094 - removed creating an AspectJ dialog here so
			// that we behave like the jdt. The error is coming out in the
			// problems view anyway (which is how jdt behaves)
		}

		return map;
	}

	public void flushClasspathCache() {
		cachedClasspath = null;
	}

	public String expandVariables(String path, String eKinds) {
		StringBuffer resultBuffer = new StringBuffer();
		StringTokenizer strTok = new StringTokenizer(path, File.pathSeparator);
		StringTokenizer strTok2 = new StringTokenizer(eKinds, File.pathSeparator);
		while (strTok.hasMoreTokens()) {
			String current = strTok.nextToken();
			int entryKind = Integer.parseInt(strTok2.nextToken());
			if (entryKind == IClasspathEntry.CPE_VARIABLE) {
				int slashPos = current.indexOf(AspectJPlugin.NON_OS_SPECIFIC_SEPARATOR, 0);
				if (slashPos != -1) {
					String exp = JavaCore.getClasspathVariable(current.substring(0, slashPos)).toOSString();
					resultBuffer.append(exp);
					resultBuffer.append(current.substring(slashPos));
				} else {
					String exp = JavaCore.getClasspathVariable(current).toOSString();
					resultBuffer.append(exp);
				}
			} else {
				resultBuffer.append(current);
			}
			resultBuffer.append(File.pathSeparator);
		}
		return resultBuffer.toString();
	}

	/**
	 * @param inputPath
	 * @return
	 */
	public String fullyQualifyPathEntries(String inputPath) {
		StringBuffer resultBuffer = new StringBuffer();
		StringTokenizer strTok = new StringTokenizer(inputPath, File.pathSeparator);
		while (strTok.hasMoreTokens()) {
			String current = strTok.nextToken();
			File f = new File(current);
			if (f.exists() && f.isAbsolute()) {
				// entry not relative to workspace (it's fully qualifed)
				resultBuffer.append(current);
			} else {
				// Try to resolve path relative to the workspace. Need to
				// replace part of the path string with a fully qualified
				// equivalent.
				String projectName = null;
				int slashPos = current.indexOf(AspectJPlugin.NON_OS_SPECIFIC_SEPARATOR, 1);
				if (slashPos != -1) {
					projectName = current.substring(1, slashPos);
				} else {
					projectName = current.substring(1);
				}

				IProject proj = AspectJPlugin.getWorkspace().getRoot().getProject(projectName);

				if (proj != null && proj.getLocation() != null) {
					String projectPath = proj.getLocation().toString();

					if (slashPos != -1) {
						String rest = current.substring(slashPos + 1);
						IResource res = proj.findMember(rest);
						if (res != null) {
							resultBuffer.append(res.getRawLocation().toOSString());
						} else {
							resultBuffer.append(projectPath + AspectJPlugin.NON_OS_SPECIFIC_SEPARATOR + rest);
						}
					} else {
						resultBuffer.append(projectPath);
					}
				}// end if named project found
				else {
					AJLog.log(AJLog.BUILDER, "AspectJ path entry " + current //$NON-NLS-1$
							+ " does not exist."); //$NON-NLS-1$
					resultBuffer.append(current);
				}// end else entry not found in workspace
			}// end if entry is relative to workspace
			resultBuffer.append(File.pathSeparator);
		}// end while more tokens to process

		String result = resultBuffer.toString();
		if (result.endsWith(File.pathSeparator)) {
			result = result.substring(0, result.length() - 1);
		}

		return result;
	}

	/**
	 * Utility method for converting a semicolon separated list of files stored in a string into a Set of java.io.File objects.
	 * 
	 */
	private Set mapStringToSet(String input, boolean validateFiles) {
		if (input.length() == 0)
			return null;
		String inputCopy = input;

		StringBuffer invalidEntries = new StringBuffer();

		// For relative paths (they don't start with a File.separator
		// or a drive letter on windows) - we prepend the projectBaseDirectory
		String projectBaseDirectory = project.getLocation().toOSString();

		Set fileSet = new HashSet();
		while (inputCopy.indexOf(java.io.File.pathSeparator) != -1) { // ASCFIXME
			// - Bit
			// too
			// platform
			// specific!
			int idx = inputCopy.indexOf(java.io.File.pathSeparator);
			String path = inputCopy.substring(0, idx);

			java.io.File f = new java.io.File(path);
			if (!f.isAbsolute())
				f = new File(projectBaseDirectory + java.io.File.separator + path);
			if (validateFiles && !f.exists()) {
				invalidEntries.append(f + "\n"); //$NON-NLS-1$
			} else {
				fileSet.add(f);
			}
			inputCopy = inputCopy.substring(idx + 1);

		}
		// Process the final element
		if (inputCopy.length() != 0) {
			java.io.File f = new java.io.File(inputCopy);
			if (!f.isAbsolute())
				f = new File(projectBaseDirectory + java.io.File.separator + inputCopy);
			if (validateFiles && !f.exists()) {
				invalidEntries.append(f + "\n"); //$NON-NLS-1$
			} else {
				fileSet.add(f);
			}

		}

		if (validateFiles && invalidEntries.length() != 0) {
			AJLog.log(AJLog.COMPILER, NLS.bind(CoreMessages.missingJarsWarning, invalidEntries.toString()));
		}
		return fileSet;
	}

	private void getProjectRelativePaths(IResource[] resource_list, List allProjectFiles, CoreUtils.FilenameFilter filter,
			int trimSegments, IClasspathEntry sourceEntry) {
		try {
			for (int i = 0; i < resource_list.length; i++) {
				IResource ir = resource_list[i];
				// bug 161739: skip excluded resources
				char[][] inclusionPatterns = ((ClasspathEntry) sourceEntry).fullInclusionPatternChars();
				char[][] exclusionPatterns = ((ClasspathEntry) sourceEntry).fullExclusionPatternChars();
				if (!Util.isExcluded(ir, inclusionPatterns, exclusionPatterns)) {
					if (ir instanceof IContainer) {
						getProjectRelativePaths(((IContainer) ir).members(), allProjectFiles, filter, trimSegments, sourceEntry);
					} else if (filter.accept(ir.getName())) {
						String[] segments = ir.getProjectRelativePath().segments();
						String path = ""; //$NON-NLS-1$
						for (int j = trimSegments; j < segments.length; j++) {
							path += segments[j];
							if (j < segments.length - 1)
								path += '/'; // matches Eclipse's separator
						}
						allProjectFiles.add(path);
					}

				}
			}
		} catch (Exception e) {
		}
	}

	private ArrayList getLinkedChildFolders(IResource resource) {
		ArrayList resultList = new ArrayList();

		if (resource instanceof IContainer) {
			try {
				IResource[] children = ((IContainer) resource).members();
				for (int i = 0; i < children.length; i++) {
					if ((children[i] instanceof IFolder) && children[i].isLinked()) {
						resultList.add(children[i]);
					}
				}
			} catch (CoreException e) {
			}
		}
		return resultList;
	}

	private String getResourceFullPath(IResource srcContainer, String relPath, ArrayList linkedFolders) {
		String result = null;
		if (relPath.lastIndexOf('/') != -1) {
			// Check to see if the relPath under scrutiny is
			// under a linked folder in this project.
			Iterator it = linkedFolders.iterator();
			while (it.hasNext()) {
				IFolder folder = (IFolder) it.next();
				String linkedFolderName = folder.getName();
				if (relPath.indexOf(linkedFolderName + "/") == 0) { //$NON-NLS-1$
					// Do the replacement ensuring that the result uses
					// operating system separator characters.
					result = folder.getLocation().toString() + relPath.substring(linkedFolderName.length());
					result = result.replace('/', File.separatorChar);
					break;
				}
			}
		}
		if (result == null) {
			result = srcContainer.getLocation().toOSString() + File.separator + relPath;
		}
		return result;
	}

	public void addModifiedFile(File changedFile) {
	    AJLog.log(AJLog.BUILDER, "File: " + changedFile + " has changed.");
	    if (modifiedFiles != null) {
	        modifiedFiles.add(changedFile);
	    } else {
	        AJLog.log(AJLog.BUILDER, "    but, we don't have any state yet, so not recording the change.");
	    }
	}
	
	/**
	 * Flag this compiler configuration as having had a change.
	 * This is reset after a call to {@link #configurationRead()}
	 * @param changeFlag change flag from 
	 * {@link CompilerConfigurationChangeFlags}
	 */
	public void configurationChanged(int changeFlag) {
	    configurationChanges |= changeFlag;
	    logConfigurationChange(changeFlag);
	}

	private void logConfigurationChange(int changeFlag) {
	    List changeKind = new ArrayList();
        if ((changeFlag & PROJECTSOURCEFILES_CHANGED) != NO_CHANGES) {
            changeKind.add("PROJECTSOURCEFILES_CHANGED");
        }
        if ((changeFlag & JAVAOPTIONS_CHANGED) != NO_CHANGES) {
            changeKind.add("JAVAOPTIONS_CHANGED");
        }
        if ((changeFlag & ASPECTPATH_CHANGED) != NO_CHANGES) {
            changeKind.add("ASPECTPATH_CHANGED");
        }
        if ((changeFlag & CLASSPATH_CHANGED) != NO_CHANGES) {
            changeKind.add("CLASSPATH_CHANGED");
        }
        if ((changeFlag & INPATH_CHANGED) != NO_CHANGES) {
            changeKind.add("INPATH_CHANGED");
        }
        if ((changeFlag & NONSTANDARDOPTIONS_CHANGED) != NO_CHANGES) {
            changeKind.add("NONSTANDARDOPTIONS_CHANGED");
        }
        if ((changeFlag & OUTJAR_CHANGED) != NO_CHANGES) {
            changeKind.add("OUTJAR_CHANGED");
        }
        if ((changeFlag & PROJECTSOURCERESOURCES_CHANGED) != NO_CHANGES) {
            changeKind.add("PROJECTSOURCERESOURCES_CHANGED");
        }
        if ((changeFlag & OUTPUTDESTINATIONS_CHANGED) != NO_CHANGES) {
            changeKind.add("OUTPUTDESTINATIONS_CHANGED");
        }
        // deprecated
        if ((changeFlag & INJARS_CHANGED) != NO_CHANGES) {
            changeKind.add("INJARS_CHANGED");
        }
        AJLog.log(AJLog.BUILDER, "CoreCompilerConfiguration for project " + project.getName() + " registered a configuration change: " + changeKind);
	}
	
	/**
	 * converts the current configuration change list to a 
	 * human readable string
	 * @return human readable string denoting all configuration
	 * changes since last read.
	 */
	private String toConfigurationString() {
        List changeKind = new ArrayList();
        if ((configurationChanges & PROJECTSOURCEFILES_CHANGED) != NO_CHANGES) {
            changeKind.add("PROJECTSOURCEFILES_CHANGED");
        }
        if ((configurationChanges & JAVAOPTIONS_CHANGED) != NO_CHANGES) {
            changeKind.add("JAVAOPTIONS_CHANGED");
        }
        if ((configurationChanges & ASPECTPATH_CHANGED) != NO_CHANGES) {
            changeKind.add("ASPECTPATH_CHANGED");
        }
        if ((configurationChanges & CLASSPATH_CHANGED) != NO_CHANGES) {
            changeKind.add("CLASSPATH_CHANGED");
        }
        if ((configurationChanges & INPATH_CHANGED) != NO_CHANGES) {
            changeKind.add("INPATH_CHANGED");
        }
        if ((configurationChanges & NONSTANDARDOPTIONS_CHANGED) != NO_CHANGES) {
            changeKind.add("NONSTANDARDOPTIONS_CHANGED");
        }
        if ((configurationChanges & OUTJAR_CHANGED) != NO_CHANGES) {
            changeKind.add("OUTJAR_CHANGED");
        }
        if ((configurationChanges & PROJECTSOURCERESOURCES_CHANGED) != NO_CHANGES) {
            changeKind.add("PROJECTSOURCERESOURCES_CHANGED");
        }
        if ((configurationChanges & OUTPUTDESTINATIONS_CHANGED) != NO_CHANGES) {
            changeKind.add("OUTPUTDESTINATIONS_CHANGED");
        }
        // deprecated
        if ((configurationChanges & INJARS_CHANGED) != NO_CHANGES) {
            changeKind.add("INJARS_CHANGED");
        }
        return changeKind.toString();
	}

	/**
	 * Callback method from AspectJ to tell us that it has processed the configuration information and is going to proceed with a
	 * build.
	 */
	public void configurationRead() {
	    // we now know nothing has changed
	    AJLog.log(AJLog.BUILDER, "Compiler configuration for project " + project.getName() + " has been read by compiler.  Resetting.");
	    AJLog.log(AJLog.BUILDER, "     Configuration was " + toConfigurationString());
	    
	    // we are still not keeping track of some changes:
	    // JAVAOPTIONS_CHANGED | NONSTANDARDOPTIONS_CHANGED | OUTJAR_CHANGED |
        // OUTPUTDESTINATIONS_CHANGED | INJARS_CHANGED
	    configurationChanges = NO_CHANGES;
	    resetModifiedList();
	}

	/**
	 * Need to tell AspectJ what has changed in the configuration since the last build was done - the lazy answer (which causes it
	 * to behave as it always used to) is EVERYTHING.
	 * 
	 * @see CompilerConfigurationChangeFlags
	 * @see AspectJCorePreferences#isIncrementalCompilationOptimizationsEnabled
	 */
	public int getConfigurationChanges() {
	    // if the optimization flag is turned off, then return EVERYTHING
	    if (!AspectJCorePreferences.isIncrementalCompilationOptimizationsEnabled()) {
            AJLog.log(AJLog.BUILDER, "Optimizations turned off, so assuming all parts of configuration have changed");
            return EVERYTHING;
	    } else {
	        AJLog.log(AJLog.BUILDER, "Sending the following configuration changes to the compiler: " + toConfigurationString());
	        return AspectJCorePreferences.isIncrementalCompilationOptimizationsEnabled() ? configurationChanges : EVERYTHING;
	    }
	}

	/**
	 * If we know, tell AspectJ a List<File> that have changed since the last build. We should be able to work this out from
	 * analysing delta changes. Returning null means we have no idea and will cause AspectJ to do the analysis to work it out.
	 */
	public List getProjectSourceFilesChanged() {
	    if (!AspectJCorePreferences.isIncrementalCompilationOptimizationsEnabled()) {
            AJLog.log(AJLog.BUILDER, "Optimizations turned off, so assuming all source files have changed");
            return null;
        } else if (modifiedFiles == null) {
            // null means we dont know
	        AJLog.log(AJLog.BUILDER, "We don't know what has changed since last build, so assuming all source files have changed");
	        return null;
	    } else {
	        AJLog.log(AJLog.BUILDER, modifiedFiles.size() + " source file changes since last build");
	        return modifiedFiles;
	    }
	}

	public void resetModifiedList() {
	    AJLog.log(AJLog.BUILDER, "Resetting list of modified source files.  Was " + 
	            (modifiedFiles == null ? "null" : modifiedFiles.toString()));
	    modifiedFiles = new ArrayList();
	}
	
    public void resetClasspathElementsWithModifiedContents() {
        classpathElementsWithModifiedContents = null;
    }
    public void setClasspathElementsWithModifiedContents(List /*String*/ modifiedContents) {
        AJLog.log(AJLog.BUILDER, "Setting list of classpath elements with modified contents:");
        AJLog.log(AJLog.BUILDER, "      " + modifiedContents.toString());
        classpathElementsWithModifiedContents = modifiedContents;
    }
	
	// must go through the classpath and look at projects we depend on that have been built before our
	// most recent last build
	public List getClasspathElementsWithModifiedContents() {
	    return classpathElementsWithModifiedContents;
	}

	
	/**
	 * helper method that grabs the compiler configuration for a particular project
	 * creates one if it does not exist
	 * @param project
	 * @return the project's compiler configuration
	 */
	public static CoreCompilerConfiguration getCompilerConfigurationForProject(IProject project) {
	    return (CoreCompilerConfiguration) AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration();
	}
	
	public File[] getChangedFiles() {
//	    Set changedFiles = AsmManager.getDefault().getAspectsWeavingFilesOnLastBuild();
//	    changedFiles.addAll(AsmManager.getDefault().getModelChangesOnLastBuild());
//
//	    return (File[]) changedFiles.toArray(new File[changedFiles.size()]);
	    CoreOutputLocationManager coreOutputLocationManager = (CoreOutputLocationManager) getOutputLocationManager();
        File[] touchedFiles = 
	        coreOutputLocationManager.getTouchedClassFiles();
        coreOutputLocationManager.resetTouchedClassFiles();
        return touchedFiles;
	    
	}
}
