/*******************************************************************************
 * Copyright (c) 2013 IBH SYSTEMS GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.scada.doc.bundle;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.tycho.core.osgitools.BundleReader;
import org.eclipse.tycho.core.osgitools.OsgiManifest;

public class JavadocRunner
{
    private File output;

    private ToolchainManager toolchainManager;

    private MavenSession session;

    private Set<File> sourceFolders;

    private Log log;

    private JavadocOptions options;

    private File buildDirectory;

    private BundleReader bundleReader;

    private Collection<String> classPath;

    public JavadocRunner ()
    {
    }

    public void setBundleReader ( final BundleReader bundleReader )
    {
        this.bundleReader = bundleReader;
    }

    public void setBuildDirectory ( final File buildDirectory )
    {
        this.buildDirectory = buildDirectory;
    }

    public void setOptions ( final JavadocOptions options )
    {
        this.options = options;
        if ( this.options == null )
        {
            this.options = new JavadocOptions ();
        }
    }

    public void setLog ( final Log log )
    {
        this.log = log;
    }

    public void setSession ( final MavenSession session )
    {
        this.session = session;
    }

    public void setOutput ( final File output )
    {
        this.output = output;
    }

    public void run () throws Exception
    {
        this.output.mkdirs ();
        this.buildDirectory.mkdirs ();

        final Commandline cli = new Commandline ();
        cli.setExecutable ( getExecutable () );
        cli.setWorkingDirectory ( this.output );

        final File optionsFile = new File ( this.buildDirectory, "javadoc.options.txt" );
        final PrintStream ps = new PrintStream ( optionsFile );
        try
        {
            cli.createArg ().setValue ( "@" + optionsFile.getAbsolutePath () );
            addJvmArgs ( ps );
            addSourcePaths ( ps );
            addClassPath ( ps );
            final int count = addPackages ( ps );

            if ( count <= 0 )
            {
                throw new MojoExecutionException ( "No packages found" );
            }

            addArguments ( ps );

            this.log.info ( "Calling: " + cli );

            ps.close ();

            final int rc = CommandLineUtils.executeCommandLine ( cli, new DefaultConsumer (), new DefaultConsumer () );
            if ( rc != 0 )
            {
                if ( !this.options.isIgnoreError () )
                {
                    throw new MojoExecutionException ( "Failed to execute javadoc - rc = " + rc );
                }
                else
                {
                    this.log.info ( "execution failed with rc = " + rc );
                }
            }
        }
        finally
        {
            ps.close ();
        }
    }

    private void addClassPath ( final PrintStream ps )
    {
        addPathArgument ( ps, "-classpath", this.classPath );
    }

    private void addArguments ( final PrintStream ps )
    {
        for ( final String argument : this.options.getAdditionalArguments () )
        {
            ps.println ( argument );
        }
    }

    private void addJvmArgs ( final PrintStream ps )
    {
        for ( final String arg : this.options.getJvmOptions () )
        {
            ps.println ( "-J" + arg );
        }
    }

    private int addPackages ( final PrintStream ps ) throws Exception
    {
        int count = 0;

        for ( final File base : this.sourceFolders )
        {
            final File manifestFile = new File ( base.getParent (), "META-INF/MANIFEST.MF" );
            if ( !manifestFile.canRead () )
            {
                this.log.debug ( "No readable manifest: " + manifestFile );
                continue;
            }

            final OsgiManifest bundle = this.bundleReader.loadManifest ( manifestFile );
            count += addPackages ( ps, bundle.getManifestElements ( "Export-Package" ) );
        }
        return count;
    }

    private int addPackages ( final PrintStream ps, final ManifestElement[] manifestElements )
    {
        if ( manifestElements == null )
        {
            return 0;
        }

        for ( final ManifestElement ele : manifestElements )
        {
            final String pkg = ele.getValue ();
            ps.println ( pkg );
        }

        return manifestElements.length;
    }

    private void addPath ( final PrintStream ps, final Collection<?> path )
    {
        boolean first = true;
        for ( final Object ele : path )
        {
            if ( !first )
            {
                ps.print ( File.pathSeparator );
            }
            else
            {
                first = false;
            }
            ps.print ( ele );
        }
    }

    private void addSourcePaths ( final PrintStream ps )
    {
        addPathArgument ( ps, "-sourcepath", this.sourceFolders );
    }

    private void addPathArgument ( final PrintStream ps, final String arg, final Collection<?> path )
    {
        ps.print ( arg + " \"" );
        addPath ( ps, path );
        ps.println ( "\"" );
    }

    protected String getExecutable ()
    {
        if ( this.options.getExecutable () != null )
        {
            // prefer the specific one
            return this.options.getExecutable ();
        }

        if ( this.toolchainManager != null )
        {
            // try the toolchain
            final Toolchain tc = this.toolchainManager.getToolchainFromBuildContext ( "jdk", this.session );
            if ( tc != null )
            {
                final String exe = tc.findTool ( "javadoc" );
                if ( exe != null )
                {
                    return exe;
                }
            }
        }

        // fall back
        return "javadoc";
    }

    public void setToolchainManager ( final ToolchainManager toolchainManager )
    {
        this.toolchainManager = toolchainManager;
    }

    public void setSourceFolders ( final Set<File> sourceFolders )
    {
        this.sourceFolders = sourceFolders;
    }

    public void setClassPath ( final Collection<String> classPath )
    {
        this.classPath = classPath;
    }
}
