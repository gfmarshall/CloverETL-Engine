/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
package org.jetel.graph.runtime;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.MDC;
import org.jetel.data.Defaults;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.graph.GraphElement;
import org.jetel.graph.IGraphElement;
import org.jetel.graph.Node;
import org.jetel.graph.Phase;
import org.jetel.graph.Result;
import org.jetel.graph.TransformationGraph;
import org.jetel.graph.runtime.jmx.CloverJMX;
import org.jetel.util.primitive.DuplicateKeyMap;


/**
 *  Description of the Class
 *
 * @author      dpavlis
 * @since       July 29, 2002
 * @revision    $Revision$
 */
public class WatchDog implements Callable<Result>, CloverPost {
    /**
     * Thread manager is used to run nodes as threads.
     */
    private IThreadManager threadManager;
    private int trackingInterval;
	private Result watchDogStatus;
	private TransformationGraph graph;
	private Phase currentPhase;
    private BlockingQueue <Message<?>> inMsgQueue;
    private DuplicateKeyMap outMsgMap;
    private Throwable causeException;
    private IGraphElement causeGraphElement;
    private CloverJMX cloverJMX;
//    private volatile boolean runIt;
    private boolean provideJMX = true;
    private boolean finishJMX = true; //whether the JMX mbean should be unregistered on the graph finish 
    private GraphRuntimeContext runtimeContext;
    
    public final static String MBEAN_NAME_PREFIX = "CLOVERJMX_";
    public final static long WAITTIME_FOR_STOP_SIGNAL = 5000000000L; //nanoseconds
    private int[] _MSG_LOCK=new int[0];
    
    private static Log logger = LogFactory.getLog(WatchDog.class);
    
    static private MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    private ObjectName jmxObjectName;


	/**
	 *Constructor for the WatchDog object
	 *
	 * @param  graph   Description of the Parameter
	 * @param  phases  Description of the Parameter
	 * @since          September 02, 2003
	 */
	public WatchDog(TransformationGraph graph, GraphRuntimeContext runtimeContext) {
		graph.setWatchDog(this);
		this.graph = graph;
		this.runtimeContext = runtimeContext;
		currentPhase = null;
		watchDogStatus = Result.N_A;
        
        inMsgQueue=new PriorityBlockingQueue<Message<?>>();
        outMsgMap=new DuplicateKeyMap(Collections.synchronizedMap(new HashMap()));
        trackingInterval=runtimeContext.getTrackingInterval();
        
        //is JMX turned on?
        provideJMX = runtimeContext.useJMX();
        
        //passes a password from context to the running graph
        graph.setPassword(runtimeContext.getPassword());
	}

	/**
	 * WatchDog initialization.
	 */
	public void init() {
		//at least simple thread manager will be used
		if(threadManager == null) {
			threadManager = new SimpleThreadManager();
		}
		
		//start up JMX
		cloverJMX = new CloverJMX(this);
		if(provideJMX) {
			registerTrackingMBean(cloverJMX);
		}

       	//watchdog is now ready to use
		watchDogStatus = Result.READY;
	}
	
	private void finishJMX() {
		if(provideJMX) {
			try {
				mbs.unregisterMBean(jmxObjectName);
			} catch (Exception e) {
				logger.error("JMX error - ObjectName cannot be unregistered.", e);
			}
		}
	}
	
	/**  Main processing method for the WatchDog object */
	public Result call() {
       	try {
    		MDC.put("runId", runtimeContext.getRunId());
    		
    		long startTimestamp = System.currentTimeMillis();
    		
            if (runtimeContext.isVerboseMode()) {
                // this can be called only after graph.init()
                graph.dumpGraphConfiguration();
            }

    		watchDogStatus = Result.RUNNING;

//    		runIt = true;
    		
    		//creates tracking logger for cloverJMX mbean
            TrackingLogger.track(cloverJMX);
          	
           	cloverJMX.graphStarted();

           	Phase[] phases = graph.getPhases();
           	
           	Result phaseResult = Result.N_A;
           	for (int currentPhaseNum = 0; currentPhaseNum < phases.length; currentPhaseNum++) {
           		cloverJMX.phaseStarted(phases[currentPhaseNum]);
                phaseResult = executePhase(phases[currentPhaseNum]);
           		cloverJMX.phaseFinished();
                if(phaseResult == Result.ABORTED)      { 
                    logger.error("!!! Phase execution aborted !!!");
                    break;
                } else if(phaseResult == Result.ERROR) {
                    logger.error("!!! Phase finished with error - stopping graph run !!!");
                    break;
                }
            }
           	watchDogStatus = phaseResult;

           	cloverJMX.graphFinished();

           	if(runtimeContext.isWaitForJMXClient()) {
           		//wait for a JMX client (GUI) to download all tracking information
	           	long restTime = WAITTIME_FOR_STOP_SIGNAL - (System.nanoTime() - cloverJMX.getGraphDetail().getStartTime());
	           	while (restTime > 0 && !cloverJMX.canCloseServer()) {
	           		try {
	    				Thread.sleep(10);
	    	           	cloverJMX.graphFinished();
	    			} catch (InterruptedException e) {
					} finally {
	                   	restTime = WAITTIME_FOR_STOP_SIGNAL - (System.nanoTime() - cloverJMX.getGraphDetail().getStartTime());
	    			}
	           	}
           	}

            if(finishJMX) {
            	finishJMX();
            }
            
            logger.info("WatchDog thread finished - total execution time: " + (System.currentTimeMillis() - startTimestamp) / 1000 + " (sec)");

            MDC.remove("runId");
       	} catch (RuntimeException e) {
       		causeException = e;
       		causeGraphElement = null;
       		watchDogStatus = Result.ERROR;
       		logger.error("Fatal error watchdog execution", e);
       		throw e;
       	}

		return watchDogStatus;
	}


    /**
     * Register given jmx mbean.
     */
    private void registerTrackingMBean(CloverJMX cloverJMX) {
        String mbeanId = graph.getId();
        
        // Construct the ObjectName for the MBean we will register
        try {
        	String name = createMBeanName(mbeanId != null ? mbeanId : graph.getName(), this.getGraphRuntimeContext().getRunId());
            jmxObjectName = new ObjectName( name );
            logger.info("register MBean with name:"+name);
            // Register the  MBean
            mbs.registerMBean(cloverJMX, jmxObjectName);

        } catch (MalformedObjectNameException e) {
            logger.error(e);
        } catch (InstanceAlreadyExistsException e) {
        	logger.error(e);
        } catch (MBeanRegistrationException e) {
        	logger.error(e);
        } catch (NotCompliantMBeanException e) {
        	logger.error(e);
        }
    }

    /**
     * Creates identifier for shared JMX mbean.
     * @param defaultMBeanName
     * @return
     */
    public static String createMBeanName(String mbeanIdentifier) {
    	return createMBeanName(mbeanIdentifier, 0);
    }

    /**
     * Creates identifier for shared JMX mbean.
     * @param mbeanIdentifier
     * @param runId
     * @return
     */
    public static String createMBeanName(String mbeanIdentifier, long runId) {
        return "org.jetel.graph.runtime:type=" + MBEAN_NAME_PREFIX + (mbeanIdentifier != null ? mbeanIdentifier : "") + "_" + runId;
    }
    
	/**
	 * Execute transformation - start-up all Nodes & watch them running
	 *
	 * @param  phase      Description of the Parameter
	 * @param  leafNodes  Description of the Parameter
	 * @return            Description of the Return Value
	 * @since             July 29, 2002
	 */
	public Result watch(Phase phase) throws InterruptedException {
		Message<?> message;
		Set<Node> phaseNodes;

		// let's create a copy of leaf nodes - we will watch them
		phaseNodes = new HashSet<Node>(phase.getNodes().values());

		// entering the loop awaiting completion of work by all leaf nodes
		while (true) {
			// wait on error message queue
			message = inMsgQueue.poll(Defaults.WatchDog.WATCHDOG_SLEEP_INTERVAL, TimeUnit.MILLISECONDS);
			if (message != null) {
				switch(message.getType()){
				case ERROR:
					causeException = ((ErrorMsgBody) message.getBody()).getSourceException();
					causeGraphElement = message.getSender();
					logger.error("!!! Fatal Error !!! - graph execution is aborting");
					logger.error("Node "
							+ message.getSender().getId()
							+ " finished with status: "
							+ ((ErrorMsgBody) message.getBody())
									.getErrorMessage() + (causeException != null ? " caused by: " + causeException.getMessage() : ""));
					logger.debug("Node " + message.getSender().getId() + " error details:", causeException);
					abort();
					return Result.ERROR;
				case MESSAGE:
					synchronized (_MSG_LOCK) {
						outMsgMap.put(message.getRecipient(), message);
					}
					break;
				case NODE_FINISHED:
					phaseNodes.remove(message.getSender());
					break;
				default:
					// do nothing, just wake up
				}
			}

			// ------------------------------------
			// Check that we still have some nodes running
			// ------------------------------------

			// is there any node running ?
			if (phaseNodes.isEmpty()) {
				logger.info("Execution of phase [" + phase.getPhaseNum()
						+ "] successfully finished - elapsed time(sec): "
						+ cloverJMX.getGraphDetail().getExecutionTime(TimeUnit.SECONDS));
				return Result.FINISHED_OK;
			}

			// -----------------------------------
			// from time to time perform some task
			// -----------------------------------
			if (message == null) {
				// gather tracking at nodes level
				cloverJMX.gatherTrackingDetails();
			}

//			if (!runIt) {
//				// we were forced to stop execution, stop all Nodes
//				logger.error("User has interrupted graph execution !!!");
//				abort();
//				causeGraphElement = null;
//				causeException = null;
//				return Result.ABORTED;
//			}
		}

	}

	/**
	 *  Gets the Status of the WatchDog
	 *
	 * @return	Result of WatchDog run-time    
	 * @since     July 30, 2002
     * @see     org.jetel.graph.Result
	 */
	public Result getStatus() {
		return watchDogStatus;
	}
	
	/**
	 * aborts execution of current phase
	 *
	 * @since    July 29, 2002
	 */
	public void abort() {
		// iterate through all the nodes and stop them
        for(Node node : currentPhase.getNodes().values()) {
			node.abort();
			logger.warn("Interrupted node: " + node.getId());
		}
        watchDogStatus=Result.ABORTED;
	}


	/**
	 *  Description of the Method
	 *
	 * @param  nodesIterator  Description of Parameter
	 * @param  leafNodesList  Description of Parameter
	 * @since                 July 31, 2002
	 */
	private void startUpNodes(Phase phase) {
		synchronized(threadManager) {
			while(threadManager.getFreeThreadsCount() < phase.getNodes().size()) { //it is sufficient, not necessary condition - so we have to time to time wake up and check it again
				try {
					threadManager.wait(); //from time to time thread is woken up to check the condition again
				} catch (InterruptedException e) {
					//DO NOTHING
				}
			}
			for(Node node: phase.getNodes().values()) {
				threadManager.executeNode(node);
				logger.debug(node.getId()+ " ... started");
			}
		}
	}


	/**
	 *  Description of the Method
	 *
	 * @param  phase  Description of the Parameter
	 * @return        Description of the Return Value
	 */
	private Result executePhase(Phase phase) {
		currentPhase = phase;
		try {
			phase.init();
		} catch (ComponentNotReadyException e) {
			logger.error("Phase initialization failed with reason: " + e.getMessage(), e);
			causeException = e;
			causeGraphElement = e.getGraphElement();
			return Result.ERROR;
		}
		logger.info("Starting up all nodes in phase [" + phase.getPhaseNum() + "]");
		startUpNodes(phase);
		logger.info("Sucessfully started all nodes in phase!");
		// watch running nodes in phase
		Result phaseStatus;
        try{
            phaseStatus = watch(phase);
        }catch(InterruptedException ex){
            phaseStatus = Result.ABORTED;
        }
        
        //now we can notify all waiting phases for free threads
        synchronized(threadManager) {
        	threadManager.releaseNodeThreads(phase.getNodes().size());
            threadManager.notifyAll();
        }
        
        phase.setResult(phaseStatus);
		return phaseStatus;
	}

	/*
	 *  private void initialize(){
	 *  }
	 */
	/**
	 * @return Returns the currentPhaseNum - which phase is currently 
	 * beeing executed
	 */
	public int getCurrentPhaseNum() {
		return currentPhase.getPhaseNum();
	}
	
	/**
	 * @return Returns the trackingInterval - how frequently is the status printed (in ms).
	 */
	public int getTrackingInterval() {
		return trackingInterval;
	}
	
	/**
	 * @param trackingInterval How frequently print the processing status (in ms).
	 */
	public void setTrackingInterval(int trackingInterval) {
		this.trackingInterval = trackingInterval;
	}
    
	public void sendMessage(Message msg) {
        inMsgQueue.add(msg);

    }

    public Message[] receiveMessage(GraphElement recipient, @SuppressWarnings("unused") final long wait) {
        Message[] msg = null;
        synchronized (_MSG_LOCK) {
            msg=(Message[])outMsgMap.getAll(recipient, new Message[0]);
            if (msg!=null) {
                outMsgMap.remove(recipient);
            }
        }
        return msg;
    }

    public boolean hasMessage(GraphElement recipient) {
        synchronized (_MSG_LOCK ){
            return outMsgMap.containsKey(recipient);
        }
    }

    public long getUsedMemory(Node node) {
        return -1;
    }

    public long getCpuTime(Node node) {
        return -1;
    }


    /**
     * Returns exception (reported by Node) which caused
     * graph to stop processing.<br>
     * 
     * @return the causeException
     * @since 7.1.2007
     */
    public Throwable getCauseException() {
        return causeException;
    }


    /**
     * Returns ID of Node which caused
     * graph to stop procesing.
     * 
     * @return the causeNodeID
     * @since 7.1.2007
     */
    public IGraphElement getCauseGraphElement() {
        return causeGraphElement;
    }


    /**
     * Stop execution of graph
     * 
     * @since 29.1.2007
     */
//    public void stopRun() {
//        this.runIt = false;
//    }


    /**
     * @return the graph
     * @since 26.2.2007
     */
    public TransformationGraph getTransformationGraph() {
        return graph;
    }


	public void setUseJMX(boolean useJMX) {
		this.provideJMX = useJMX;
	}

	public GraphRuntimeContext getGraphRuntimeContext() {
		return runtimeContext;
	}

	public CloverJMX getCloverJmx() {
		return cloverJMX;
	}

	public boolean isFinishJMX() {
		return finishJMX;
	}

	public void setFinishJMX(boolean finishJMX) {
		this.finishJMX = finishJMX;
	}

	public IThreadManager getThreadManager() {
		return threadManager;
	}

	public void setThreadManager(IThreadManager threadManager) {
		this.threadManager = threadManager;
	}

	public TransformationGraph getGraph() {
		return graph;
	}
	
}

