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

package org.epics.pvaccess.client.impl.remote;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import org.epics.pvaccess.CAException;
import org.epics.pvaccess.PVFactory;
import org.epics.pvaccess.impl.remote.QoS;
import org.epics.pvaccess.impl.remote.Transport;
import org.epics.pvaccess.impl.remote.TransportSendControl;
import org.epics.pvaccess.impl.remote.TransportSender;
import org.epics.pvaccess.impl.remote.request.DataResponse;
import org.epics.pvaccess.impl.remote.request.SubscriptionRequest;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.Requester;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Status.StatusType;
import org.epics.pvdata.pv.StatusCreate;

/**
 * Base channel request.
 * @author <a href="mailto:matej.sekoranjaATcosylab.com">Matej Sekoranja</a>
 * @version $Id$
 */
abstract class BaseRequestImpl implements DataResponse, SubscriptionRequest, TransportSender {

    protected static final StatusCreate statusCreate = PVFactory.getStatusCreate();
    protected static final Status okStatus = statusCreate.getStatusOK();
    protected static final Status destroyedStatus = statusCreate.createStatus(StatusType.ERROR, "request destroyed", null);
    protected static final Status channelNotConnected = statusCreate.createStatus(StatusType.ERROR, "channel not connected", null);
    protected static final Status otherRequestPendingStatus = statusCreate.createStatus(StatusType.ERROR, "other request pending", null);
    protected static final PVDataCreate pvDataCreate = PVFactory.getPVDataCreate();

    /**
	 * Channel.
	 */
	protected final ChannelImpl channel;

	/**
	 * Context.
	 */
	protected final ClientContextImpl context;

	/**
	 * I/O ID given by the context when registered.
	 */
	protected final int ioid;

	/**
	 * Response callback listener.
	 */
	protected final Requester requester;

	/**
	 * Destroyed flag.
	 */
	protected volatile boolean destroyed = false;
	
	/**
	 * Remote instance destroyed.
	 */
	protected volatile boolean remotelyDestroyed = false;
	
	protected int pendingRequest = NULL_REQUEST;
	/* negative... */
	protected static final int NULL_REQUEST = -1;
	protected static final int PURE_DESTROY_REQUEST = -2;
	
	protected final ReentrantLock lock = new ReentrantLock();
	
	public BaseRequestImpl(ChannelImpl channel, Requester requester)
	{
		if (requester == null)
			throw new IllegalArgumentException("requester == null");

		this.channel = channel;
		this.context = (ClientContextImpl)channel.getContext();
		
		this.requester = requester;

		// register response request
		ioid = context.registerResponseRequest(this);
		channel.registerResponseRequest(this);
	}

	public final boolean startRequest(int qos) {
		synchronized (this) {
			// we allow pure destroy...
			if (pendingRequest != NULL_REQUEST && qos != PURE_DESTROY_REQUEST)
				return false;
			
			pendingRequest = qos;
			return true;
		}
	}
	
	public final void stopRequest() {
		synchronized (this) {
			pendingRequest = NULL_REQUEST;
		}
	}
	
	public final int getPendingRequest() {
		synchronized (this) {
			return pendingRequest;
		}
	}

	/* (non-Javadoc)
	 * @see org.epics.pvaccess.impl.remote.ResponseRequest#getRequester()
	 */
	@Override
	public Requester getRequester() {
		return requester;
	}

	/* (non-Javadoc)
	 * @see org.epics.pvaccess.core.ResponseRequest#getIOID()
	 */
	public int getIOID() {
		return ioid;
	}

	abstract void initResponse(Transport transport, byte version, ByteBuffer payloadBuffer, byte qos, Status status);
	abstract void destroyResponse(Transport transport, byte version, ByteBuffer payloadBuffer, byte qos, Status status);
	abstract void normalResponse(Transport transport, byte version, ByteBuffer payloadBuffer, byte qos, Status status);
	
	/* (non-Javadoc)
	 * @see org.epics.pvaccess.core.DataResponse#response(org.epics.pvaccess.core.Transport, byte, java.nio.ByteBuffer)
	 */
	public void response(Transport transport, byte version, ByteBuffer payloadBuffer) {
		boolean cancel = false;
		try
		{	
			transport.ensureData(1);
			final byte qos = payloadBuffer.get();
			final Status status = statusCreate.deserializeStatus(payloadBuffer, transport);

			if (QoS.INIT.isSet(qos))
			{
				initResponse(transport, version, payloadBuffer, qos, status);
			}
			else if (QoS.DESTROY.isSet(qos))
			{
				remotelyDestroyed = true;
				cancel = true;

				destroyResponse(transport, version, payloadBuffer, qos, status);
			}
			else
			{
				normalResponse(transport, version, payloadBuffer, qos, status);
			}
		}
		finally
		{
			// always cancel request
			if (cancel)
				cancel();
		}
	}

	/* (non-Javadoc)
	 * @see org.epics.pvaccess.core.ResponseRequest#cancel()
	 */
	public void cancel() {
		destroy();
	}

	/* (non-Javadoc)
	 * @see org.epics.pvaccess.client.ChannelRequest#destroy()
	 */
	public void destroy() {
		
		synchronized (this) {
			if (destroyed)
				return;
			destroyed = true;
		}

		// unregister response request
		context.unregisterResponseRequest(this);
		channel.unregisterResponseRequest(this);

		// destroy remote instance
		if (!remotelyDestroyed)
		{
			startRequest(PURE_DESTROY_REQUEST);
			try {
				channel.checkAndGetTransport().enqueueSendRequest(this);
			} catch (IllegalStateException ise) {
				// noop, we are just not connected
			}
		}
		
	}
	
	/* (non-Javadoc)
	 * @see org.epics.pvaccess.core.ResponseRequest#timeout()
	 */
	public void timeout() {
		cancel();
		// TODO notify?
	}

	/* (non-Javadoc)
	 * @see org.epics.pvaccess.core.ResponseRequest#reportStatus(org.epics.pvdata.pv.Status)
	 */
	public void reportStatus(Status status) {
		// destroy, since channel (parent) was destroyed
		if (status == ChannelImpl.channelDestroyed)
			destroy();
		else if (status == ChannelImpl.channelDisconnected)
			stopRequest();
		// TODO notify?
	}

	/* (non-Javadoc)
	 * @see org.epics.pvaccess.core.SubscriptionRequest#updateSubscription()
	 */
	@Override
	public void updateSubscription() throws CAException {
		// default is noop
	}
	
	/* (non-Javadoc)
	 * @see org.epics.pvaccess.impl.remote.TransportSender#lock()
	 */
	@Override
	public void lock() {
		lock.lock();
	}
	
	/* (non-Javadoc)
	 * @see org.epics.pvaccess.impl.remote.TransportSender#send(java.nio.ByteBuffer, org.epics.pvaccess.impl.remote.TransportSendControl)
	 */
	@Override
	public void send(ByteBuffer buffer, TransportSendControl control) {
		final int qos = getPendingRequest();
		if (qos == -1)
			return;
		else if (qos == PURE_DESTROY_REQUEST)
		{
			control.startMessage((byte)15, 2*Integer.SIZE/Byte.SIZE);
			buffer.putInt(channel.getServerChannelID());
			buffer.putInt(ioid);
		}
		stopRequest();
	}

	/* (non-Javadoc)
	 * @see org.epics.pvaccess.impl.remote.TransportSender#unlock()
	 */
	@Override
	public void unlock() {
		lock.unlock();
	}

}