/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS JavaIOC is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.ca.client;

import org.epics.pvData.pv.PVField;

/**
 * Interface implemented by code that can provide access to the record
 * to which a channel connects.
 * @author mrk
 *
 */
public interface ChannelProvider {
	
	/** Minimal priority. */
	static final public short PRIORITY_MIN = 0;
	/** Maximal priority. */
	static final public short PRIORITY_MAX = 99;
	/** Default priority. */
	static final public short PRIORITY_DEFAULT = PRIORITY_MIN;
	/** DB links priority. */
	static final public short PRIORITY_LINKS_DB = PRIORITY_MAX;
	/** Archive priority. */
	static final public short PRIORITY_ARCHIVE = (PRIORITY_MAX + PRIORITY_MIN) / 2;
	/** OPI priority. */
	static final public short PRIORITY_OPI = PRIORITY_MIN;

	/**
     * Terminate.
     */
    void destroy();
    /**
     * Get the provider name.
     * @return The name.
     */
    String getProviderName();
    /**
     * Find a channel.
     * @param channelName The channel name.
     * @param channelFindRequester The requester.
     * @return An interface for the find.
     */
    ChannelFind channelFind(String channelName,ChannelFindRequester channelFindRequester);
    /**
     * Query channel provider.
     * @param query The query.
     * @param queryRequester The requester.
     * @return An interface for the query, <code>null</code> if not supported.
     */
    Query query(PVField query, QueryRequester queryRequester);
    /**
     * Create a channel.
     * @param channelName The name of the channel.
     * @param channelRequester The requester.
     * @param priority channel priority, must be <code>PRIORITY_MIN</code> <= priority <= <code>PRIORITY_MAX</code>.
     * @return <code>Channel</code> instance. If channel does not exist <code>null</code> is returned and <code>channelRequester</code> notified.
     */
    Channel createChannel(String channelName,ChannelRequester channelRequester,short priority);
}