/*
 * Copyright (c) 2009 by Cosylab
 *
 * The full license specifying the redistribution, modification, usage and other
 * rights and obligations is included with the distribution of this project in
 * the file "LICENSE-CAJ". If the license is not included visit Cosylab web site,
 * <http://www.cosylab.com>.
 *
 * THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND, NOT EVEN THE
 * IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR OF THIS SOFTWARE, ASSUMES
 * _NO_ RESPONSIBILITY FOR ANY CONSEQUENCE RESULTING FROM THE USE, MODIFICATION,
 * OR REDISTRIBUTION OF THIS SOFTWARE.
 */

package org.epics.ca.server;

import java.io.PrintStream;

import org.epics.ca.CAException;
import org.epics.ca.Version;
import org.epics.ca.client.ChannelAccess;
import org.epics.ca.server.plugins.BeaconServerStatusProvider;

/**
 * The class representing a CA Server context.
 * @author <a href="mailto:matej.sekoranjaATcosylab.com">Matej Sekoranja</a>
 * @version $Id$
 */
public interface ServerContext {
  
	/**
	 * Get context implementation version.
	 * @return version of the context implementation.
	 */
	public Version getVersion();

	/**
	 * Set <code>ChannelAccess</code> implementation and initialize server.
	 * @param channelAccess implementation of channel access to be served.
	 */
	public void initialize(ChannelAccess channelAccess) throws CAException, IllegalStateException;
 
	/**
	 * Run server (process events).
	 * @param	seconds	time in seconds the server will process events (method will block), if <code>0</code>
	 * 				the method would block until <code>destroy()</code> is called.
	 * @throws IllegalStateException	if server is already destroyed.
	 * @throws CAException
	 */
	public void run(int seconds) throws CAException, IllegalStateException;
  
	/**
	 * Shutdown (stop executing run() method) of this context.
	 * After shutdown Context cannot be rerun again, destroy() has to be called to clear all used resources.
	 * @throws IllegalStateException if the context has been destroyed.
	 */
	public void shutdown() throws CAException, IllegalStateException;

	/**
	 * Clear all resources attached to this context.
	 * @throws IllegalStateException if the context has been destroyed.
	 */
	public void destroy() throws CAException, IllegalStateException;

	/**
	 * Prints detailed information about the context to the standard output stream.
	 */
	public void printInfo();

	/**
	 * Prints detailed information about the context to the specified output stream.
	 */
	public void printInfo(PrintStream out);

	/**
	 * Dispose (destroy) server context.
	 * This calls <code>destroy()</code> and silently handles all exceptions.
	 */
	public void dispose();
  
	// ************************************************************************** //
	// **************************** [ Plugins ] ********************************* //
	// ************************************************************************** //
  
	/**
	 * Set beacon server status provider.
	 * @param beaconServerStatusProvider <code>BeaconServerStatusProvider</code> implementation to set.
	 */
	public void setBeaconServerStatusProvider(BeaconServerStatusProvider beaconServerStatusProvider);

}


