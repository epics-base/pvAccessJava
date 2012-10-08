/**
 * EPICS JavaIOC is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.caV3;

import gov.aps.jca.CAException;
import gov.aps.jca.Context;
import gov.aps.jca.JCALibrary;
import gov.aps.jca.event.ContextExceptionEvent;
import gov.aps.jca.event.ContextExceptionListener;
import gov.aps.jca.event.ContextMessageEvent;
import gov.aps.jca.event.ContextMessageListener;
import gov.aps.jca.event.ContextVirtualCircuitExceptionEvent;

import org.epics.pvaccess.client.Channel;
import org.epics.pvaccess.client.ChannelAccessFactory;
import org.epics.pvaccess.client.ChannelFind;
import org.epics.pvaccess.client.ChannelFindRequester;
import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.client.ChannelProviderFactory;
import org.epics.pvaccess.client.ChannelRequester;
import org.epics.pvdata.misc.RunnableReady;
import org.epics.pvdata.misc.ThreadCreate;
import org.epics.pvdata.misc.ThreadCreateFactory;
import org.epics.pvdata.misc.ThreadPriority;
import org.epics.pvdata.misc.ThreadReady;
import org.epics.pvdata.pv.Status;

/**
 * Factory and implementation of Channel Access V3 client.
 * @author mrk
 *
 */
public class ClientFactory  {
    private static ChannelProviderImpl channelProvider = null;
    private static final ThreadCreate threadCreate = ThreadCreateFactory.getThreadCreate();
	private static ChannelProviderFactoryImpl factory = null; 

    public static final String PROVIDER_NAME = "caV3";

    private static class ChannelProviderFactoryImpl implements ChannelProviderFactory
    {

		@Override
		public String getFactoryName() {
			return PROVIDER_NAME;
		}

		@Override
		public synchronized ChannelProvider sharedInstance() {
	        try
	        {
	        	if (channelProvider == null)
	        		channelProvider = new ChannelProviderImpl();
	        	
				return channelProvider;
	        } catch (Throwable e) {
	            throw new RuntimeException("Failed to initialize shared CA client instance.", e);
	        }
		}

		@Override
		public ChannelProvider newInstance() {
	        try
	        {
				return new ChannelProviderImpl();
	        } catch (Throwable e) {
	            throw new RuntimeException("Failed to initialize new CA client instance.", e);
	        }
		}
    	
		public synchronized void destroySharedInstance() {
			if (channelProvider != null)
			{
				channelProvider.destroy();
				channelProvider = null;
			}
		}
    }

    /**
     * Registers CA client channel provider factory.
     */
    public static synchronized void start() {
        if (factory != null) return;
        factory = new ChannelProviderFactoryImpl();
        ChannelAccessFactory.registerChannelProviderFactory(factory);
    }
    
    /**
     * Unregisters CA client channel provider factory and destroys shared channel provider instance (if necessary).
     */
    public static synchronized void stop() {
    	if (factory != null)
    	{
    		ChannelAccessFactory.unregisterChannelProviderFactory(factory);
    		factory.destroySharedInstance();
    	}
    }

    private static class ChannelProviderImpl
    implements ChannelProvider,ContextExceptionListener, ContextMessageListener
    {
        private final Context context;
        private final CAThread caThread;
        
        ChannelProviderImpl() {
        	Context c = null;
            try {
            	c = JCALibrary.getInstance().createContext(JCALibrary.CHANNEL_ACCESS_JAVA);
            } catch (Throwable e) {
                e.printStackTrace();
                context = null;
                caThread = null;
                return;
            }
            context = c;
        	
            CAThread t;
            try {
                context.addContextExceptionListener(this);
                context.addContextMessageListener(this);
                t = new CAThread("cav3",ThreadPriority.getJavaPriority(ThreadPriority.low), context);
            } catch (Throwable e) {
                e.printStackTrace();
                caThread = null;
                return;
            }     
            caThread = t;
        } 
        /* (non-Javadoc)
         * @see org.epics.ioc.channelAccess.ChannelProvider#destroy()
         */
        @Override
        public void destroy() {
            caThread.stop();
            try {
                context.destroy();
            } catch (CAException e) {
                e.printStackTrace();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelProvider#channelFind(java.lang.String, org.epics.pvaccess.client.ChannelFindRequester)
         */
        @Override
        public ChannelFind channelFind(String channelName,ChannelFindRequester channelFindRequester) {
            LocateFind locateFind = new LocateFind(this,channelName,context);
            locateFind.find(channelFindRequester);
            return locateFind;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelProvider#createChannel(java.lang.String, org.epics.pvaccess.client.ChannelRequester, short)
         */
        @Override
        public Channel createChannel(String channelName,
                ChannelRequester channelRequester, short priority)
        {
            LocateFind locateFind = new LocateFind(this,channelName,context);
            return locateFind.create(channelRequester);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelProvider#createChannel(java.lang.String, org.epics.pvaccess.client.ChannelRequester, short, java.lang.String)
         */
        @Override
		public Channel createChannel(String channelName,
				ChannelRequester channelRequester, short priority,
				String address) {
        	if (address != null)
        		throw new IllegalArgumentException("address not allowed for caV3 implementation");
			return createChannel(channelName, channelRequester, priority);
		}
		/* (non-Javadoc)
         * @see org.epics.ioc.channelAccess.ChannelProvider#getProviderName()
         */
        public String getProviderName() {
            return PROVIDER_NAME;
        }
        /* (non-Javadoc)
         * @see gov.aps.jca.event.ContextExceptionListener#contextException(gov.aps.jca.event.ContextExceptionEvent)
         */
        public void contextException(ContextExceptionEvent arg0) {
            String message = arg0.getMessage();
            System.err.println(message);
            System.err.flush();
        }
        /* (non-Javadoc)
         * @see gov.aps.jca.event.ContextExceptionListener#contextVirtualCircuitException(gov.aps.jca.event.ContextVirtualCircuitExceptionEvent)
         */
        public void contextVirtualCircuitException(ContextVirtualCircuitExceptionEvent arg0) {
            String message = "status " + arg0.getStatus().toString();
            System.err.println(message);
            System.err.flush();
        }
        /* (non-Javadoc)
         * @see gov.aps.jca.event.ContextMessageListener#contextMessage(gov.aps.jca.event.ContextMessageEvent)
         */
        public void contextMessage(ContextMessageEvent arg0) {
            String message = arg0.getMessage();
            System.out.println(message);
            System.out.flush();
        }
    }
    
    private static class LocateFind implements ChannelFind,ChannelFindRequester{
        
        private final ChannelProvider channelProvider;
        private volatile ChannelFindRequester channelFindRequester = null;
        private volatile BaseV3Channel v3Channel = null;
        private final String channelName;
        private final Context context;
        
        
        LocateFind(ChannelProvider channelProvider, String channelName, Context context) {
        	this.channelProvider = channelProvider;
            this.channelName = channelName;
            this.context = context;
        }
        
        void find(ChannelFindRequester channelFindRequester) {
            this.channelFindRequester = channelFindRequester;
            v3Channel = new BaseV3Channel(channelProvider,
                    this,null,context,channelName);
            v3Channel.connectCaV3();
        }
        
        Channel create(ChannelRequester channelRequester) {
            v3Channel = new BaseV3Channel(channelProvider,
                    null,channelRequester,context,channelName);
            v3Channel.connectCaV3();
            return v3Channel;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelFind#cancelChannelFind()
         */
        @Override
        public void cancelChannelFind() {
            v3Channel.destroy();
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelFind#getChannelProvider()
         */
        @Override
        public ChannelProvider getChannelProvider() {
            return channelProvider;
        }

        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelFindRequester#channelFindResult(Stayus,org.epics.pvaccess.client.ChannelFind, boolean)
         */
        @Override
        public void channelFindResult(Status status, ChannelFind channelFind, boolean wasFound) {
            channelFindRequester.channelFindResult(status, channelFind, wasFound);
            v3Channel.destroy();
        }
    }
    
    private static class CAThread implements RunnableReady {
        private final Thread thread;
        private final Context context;
        private CAThread(String threadName,int threadPriority, Context context)
        {
            this.context = context;
            this.thread = threadCreate.create(threadName, threadPriority, this);
        }         
        /* (non-Javadoc)
         * @see org.epics.ioc.util.RunnableReady#run(org.epics.ioc.util.ThreadReady)
         */
        public void run(ThreadReady threadReady) {        
System.out.println("CaV3Client");
context.printInfo();
System.out.println();
            threadReady.ready();
            try {
                while(true) {
                    try {
                        context.poll();
                    } catch (CAException e) {
                        System.out.println(e.getMessage());
                        break;
                    }
                    Thread.sleep(5);
                }
            } catch(InterruptedException e) {

            }
        }
        
        private void stop() {
            thread.interrupt();
        }
    }
}
