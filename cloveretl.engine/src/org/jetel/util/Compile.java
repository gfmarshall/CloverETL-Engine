/*
 *  jETeL/Clover - Java based ETL application framework.
 *  Copyright (C) 2002  David Pavlis
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  Created on May 31, 2003
 */
package org.jetel.util;
import com.sun.tools.javac.Main;

import java.io.File;
import java.io.IOException;
import org.jetel.exception.*;

/**
 * @author      Wes Maciorowski, David Pavlis
 * @since
 * @revision    $Revision$
 */
public class Compile {

	private final static String compilerClassname = "sun.tools.javac.Main";
	private final static String compilerExecutable = "javac";

	private String srcFile;
	private String destDir;
	private String fileSeparator;
	private boolean forceCompile;
	private boolean compiled;
	private boolean useExecutable = false;


	/**
	 *Constructor for the JavaCompiler object
	 *
	 * @param  srcFile  Description of the Parameter
	 */
	public Compile(String srcFile) {
		this.srcFile = srcFile;
		destDir = System.getProperty("java.io.tmpdir", ".");
		forceCompile = false;
		compiled = false;
		fileSeparator = System.getProperty("file.separator", "/");
	}


	/**
	 *Constructor for the JavaCompiler object
	 *
	 * @param  srcFile  Description of the Parameter
	 * @param  destDir  Description of the Parameter
	 */
	public Compile(String srcFile, String destDir) {
		this.srcFile = srcFile;
		this.destDir = destDir;
		forceCompile = false;
		compiled = false;
		fileSeparator = System.getProperty("file.separator", "/");
	}


	/**
	 *  Sets the forceCompile attribute of the JavaCompiler object
	 *
	 * @param  forceCompile  The new forceCompile value
	 */
	public void setForceCompile(boolean forceCompile) {
		this.forceCompile = forceCompile;
	}


	/**
	 *  Description of the Method
	 *
	 * @return    Description of the Return Value
	 */
	public int compile() {
		int status = 0;
		File source = new File(srcFile);

		if (forceCompile || needsRecompile()) {
			if (useExecutable) {
				String[] args = new String[]{compilerExecutable, "-d", destDir, srcFile,
						"-Xstdout", destDir + source.getName() + ".err"};
				Runtime runtime = Runtime.getRuntime();
				try{
					status = runtime.exec(args).waitFor();
				}catch(Exception ex){
					status=-1;
				}

			} else {
				String[] args = new String[]{"-d", destDir, srcFile,
						"-Xstdout", destDir + source.getName() + ".err"};

				status = com.sun.tools.javac.Main.compile(args);

			}
			if (status==0) { 
			compiled = true;
			}
		}
		
		return status;
	}


	/**
	 *  Gets the compiledClassPath attribute of the JavaCompiler object
	 *
	 * @return    The compiledClassPath value
	 */
	public String getCompiledClassPath() {
		return destDir;
	}


	/**
	 *  Description of the Method
	 *
	 * @return    true if souce file needs recompiling(was modified after compilation), otherwise false
	 */
	private boolean needsRecompile() {
		File source = new File(srcFile);
		File dest = new File(destDir + fileSeparator + source.getName());
		try {
			if (dest.exists() && (dest.lastModified() >= source.lastModified())) {
				return false;// is already compiled
			} else {
				return true;
			}

		} catch (Exception ex) {
			return true;// needs recompile
		}
	}


	/*
	 *  static{
	 *  / Find compiler class:
	 *  try
	 *  {
	 *  compilerClass = Class.forName(compilerClassname);
	 *  compilerConstructor = compilerClass.getConstructor(constructorSignature);
	 *  / Get the method "compile(String[] arguments)".
	 *  / The method has the same signature on the classic and modern
	 *  / compiler, but they have different return types (boolean/int).
	 *  / Since the return type is ignored here, it doesn't matter.
	 *  Class[] methodSignature = { String[].class };
	 *  compilerMethod = compilerClass.getMethod("compile", methodSignature);
	 *  }
	 *  catch (ClassNotFoundException cnf)
	 *  {
	 *  }
	 *  catch (Exception e)
	 *  {
	 *  }
	 *  }
	 */
}

